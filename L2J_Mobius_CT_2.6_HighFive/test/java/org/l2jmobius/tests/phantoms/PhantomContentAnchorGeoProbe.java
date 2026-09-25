package org.l2jmobius.tests.phantoms;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.l2jmobius.gameserver.config.GeoEngineConfig;
import org.l2jmobius.gameserver.config.ServerConfig;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.geoengine.HermeticGeoMovement;
import org.l2jmobius.gameserver.geoengine.StaticXmlCollisionOracle;

/** Bounded native-territory anchor selection; no DB or server lifecycle. */
public final class PhantomContentAnchorGeoProbe
{
	private static final String INPUT_HEADER = "coverage_key\tfamily\tcandidate_index\tx\ty\tz_hint\tmin_z\tmax_z\tlocal_x\tlocal_y";
	private static final String OUTPUT_HEADER = "coverage_key\tfamily\tstatus\tcandidate_index\tanchor_x\tanchor_y\tanchor_z\tlocal_x\tlocal_y\tlocal_z\tattempts\tcollision_proof";

	private static final class Result
	{
		private final String key;
		private final String family;
		private String status = "NO_CANDIDATE";
		private String candidateIndex = "";
		private String anchorX = "";
		private String anchorY = "";
		private String anchorZ = "";
		private String localX = "";
		private String localY = "";
		private String localZ = "";
		private int attempts;
		private String collisionProof = "NOT_APPLICABLE";

		private Result(String key, String family)
		{
			this.key = key;
			this.family = family;
		}

		private String line()
		{
			return String.join("\t", key, family, status, candidateIndex, anchorX, anchorY, anchorZ, localX, localY, localZ, Integer.toString(attempts), collisionProof);
		}
	}

	public static void main(String[] args) throws Exception
	{
		if (args.length != 2)
		{
			throw new IllegalArgumentException("Usage: candidate-tsv output-tsv");
		}
		ServerConfig.DATAPACK_ROOT = new File(".").getCanonicalFile();
		GeoEngineConfig.load();
		if (GeoEngineConfig.PATHFINDING != 2)
		{
			throw new IllegalStateException("PathFinding=2 required");
		}
		final GeoEngine geo = GeoEngine.getInstance();
		final Path data = ServerConfig.DATAPACK_ROOT.toPath().resolve("data");
		final StaticXmlCollisionOracle collision = StaticXmlCollisionOracle.load(data.resolve("Doors.xml"), data.resolve("FenceData.xml"));
		final List<String> input = Files.readAllLines(Path.of(args[0]), StandardCharsets.UTF_8);
		if (input.isEmpty() || !INPUT_HEADER.equals(input.getFirst()))
		{
			throw new IllegalArgumentException("Unexpected content candidate header");
		}
		final Map<String, Result> results = new LinkedHashMap<>();
		for (int i = 1; i < input.size(); i++)
		{
			final String[] row = input.get(i).split("\t", -1);
			if ((row.length != 10) || !row[0].matches("[0-9a-f]{64}"))
			{
				throw new IllegalArgumentException("Malformed content candidate row " + i);
			}
			final Result result = results.computeIfAbsent(row[0], key -> new Result(key, row[1]));
			if (!result.family.equals(row[1]))
			{
				throw new IllegalArgumentException("Content candidate family drift");
			}
			if ("VALID".equals(result.status))
			{
				continue;
			}
			result.attempts++;
			final int x = Integer.parseInt(row[3]);
			final int y = Integer.parseInt(row[4]);
			final int hint = Integer.parseInt(row[5]);
			final int minimum = Integer.parseInt(row[6]);
			final int maximum = Integer.parseInt(row[7]);
			final int localX = Integer.parseInt(row[8]);
			final int localY = Integer.parseInt(row[9]);
			if (!geo.hasGeo(x, y) || !geo.hasGeo(localX, localY))
			{
				result.status = "NO_GEODATA";
				continue;
			}
			final int z = geo.getHeight(x, y, hint);
			final int localZ = geo.getHeight(localX, localY, z);
			if ((z != geo.getHeight(x, y, hint)) || (z != geo.getHeight(x, y, z)) || (localZ != geo.getHeight(localX, localY, z)) || (localZ != geo.getHeight(localX, localY, localZ)))
			{
				result.status = "NO_STABLE_Z";
				continue;
			}
			if ((z < minimum) || (z > maximum) || (localZ < minimum) || (localZ > maximum))
			{
				result.status = "OUTSIDE_NATIVE_Z";
				continue;
			}
			collision.resetIntersections();
			final boolean moved = HermeticGeoMovement.canMove(geo, x, y, z, localX, localY, localZ, 0, collision);
			if ((collision.doorIntersections() != 0) || (collision.fenceIntersections() != 0))
			{
				result.status = "STATIC_XML_BLOCKED";
				continue;
			}
			if (!moved)
			{
				result.status = "NO_LOCAL_MOVEMENT";
				continue;
		}
			result.status = "VALID";
			result.candidateIndex = row[2];
			result.anchorX = row[3];
			result.anchorY = row[4];
			result.anchorZ = Integer.toString(z);
			result.localX = row[8];
			result.localY = row[9];
			result.localZ = Integer.toString(localZ);
			result.collisionProof = "STATIC_XML_CLEAR";
		}
		final StringBuilder output = new StringBuilder(OUTPUT_HEADER).append('\n');
		int valid = 0;
		for (Result result : results.values())
		{
			output.append(result.line()).append('\n');
			if ("VALID".equals(result.status))
			{
				valid++;
			}
		}
		Files.writeString(Path.of(args[1]), output.toString(), StandardCharsets.UTF_8);
		System.out.println("CONTENT_ANCHORS groups=" + results.size() + " valid=" + valid + " candidateRows=" + (input.size() - 1));
	}
}
