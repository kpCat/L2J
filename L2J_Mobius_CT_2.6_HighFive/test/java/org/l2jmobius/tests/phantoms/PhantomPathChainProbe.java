package org.l2jmobius.tests.phantoms;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.l2jmobius.gameserver.config.GeoEngineConfig;
import org.l2jmobius.gameserver.config.ServerConfig;
import org.l2jmobius.gameserver.data.xml.DoorData;
import org.l2jmobius.gameserver.data.xml.FenceData;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.geoengine.pathfinding.GeoLocation;
import org.l2jmobius.gameserver.geoengine.pathfinding.PathFinding;

/** DB-free bounded corridor search. No route is published by this probe. */
public final class PhantomPathChainProbe
{
	private static final int EXPANSION = 8192;
	private static final int SPACING = 2048;

	public static void main(String[] args) throws Exception
	{
		if ((args.length != 5) && (args.length != 9))
		{
			throw new IllegalArgumentException("Usage: factual-points.tsv source-id target-id output.tsv factual|grid|refine|gap [frontier1-x frontier1-y frontier2-x frontier2-y]");
		}
		ServerConfig.DATAPACK_ROOT = new File(".").getCanonicalFile();
		GeoEngineConfig.load();
		if (GeoEngineConfig.PATHFINDING != 2)
		{
			throw new IllegalStateException("PathFinding=2 required");
		}
		final int maximumBuffer = PhantomGeoValidationRules.maximumBuffer(GeoEngineConfig.PATHFIND_BUFFERS);
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
		if (!lines.getFirst().equals("point_id\tpoint_kind\tx\ty\tz\tsource_refs"))
		{
			throw new IllegalArgumentException("Unexpected factual point header");
		}
		final ArrayList<PhantomPathChainSearch.Waypoint> factual = new ArrayList<>();
		for (int i = 1; i < lines.size(); i++)
		{
			final String[] row = lines.get(i).split("\t", -1);
			if (row.length != 6)
			{
				throw new IllegalArgumentException("Malformed factual point: " + i);
			}
			factual.add(new PhantomPathChainSearch.Waypoint(row[0], new PhantomGeoValidationRules.Point(Integer.parseInt(row[2]), Integer.parseInt(row[3]), Integer.parseInt(row[4]), 0), row[1], row[5]));
		}
		final PhantomPathChainSearch.Waypoint source = factual.stream().filter(p -> p.id().equals(args[1])).findFirst().orElseThrow();
		final PhantomPathChainSearch.Waypoint target = factual.stream().filter(p -> p.id().equals(args[2])).findFirst().orElseThrow();
		final ArrayList<PhantomPathChainSearch.Waypoint> points = new ArrayList<>(factual);
		if (args[4].equals("grid") || args[4].equals("refine") || args[4].equals("gap"))
		{
			addGrid(points, factual, source, target, geo, !args[4].equals("grid"));
			if (args[4].equals("gap"))
			{
				if (args.length != 9)
				{
					throw new IllegalArgumentException("gap mode requires two frontier coordinates");
				}
				for (int i = 5; i < 9; i += 2)
				{
					final PhantomPathChainSearch.Waypoint center = new PhantomPathChainSearch.Waypoint("frontier", new PhantomGeoValidationRules.Point(Integer.parseInt(args[i]), Integer.parseInt(args[i + 1]), 0, 0), "ROUTE", "diagnostic");
					addFrontier(points, factual, source, target, geo, center);
				}
			}
		}
		else if (!args[4].equals("factual"))
		{
			throw new IllegalArgumentException("Unknown mode: " + args[4]);
		}
		points.sort(Comparator.comparing(PhantomPathChainSearch.Waypoint::id));
		for (PhantomPathChainSearch.Waypoint neighbor : points.stream().filter(p -> !p.id().equals(source.id())).sorted(Comparator.comparingLong((PhantomPathChainSearch.Waypoint p) -> distanceSquared(source, p)).thenComparing(PhantomPathChainSearch.Waypoint::id)).limit(8).toList())
		{
			final int required = PhantomGeoValidationRules.requiredBuffer(source.point(), neighbor.point());
			final PhantomGeoValidationRules.RouteProof status = PhantomGeoValidationRules.route(source.point(), neighbor.point(), probe, maximumBuffer);
			System.out.println("SOURCE_NEIGHBOR " + neighbor.id() + " " + neighbor.point() + " required=" + required + " status=" + status.reason());
		}
		PhantomPathChainSearch.SearchOutcome outcome = PhantomPathChainSearch.searchDetailed(points, source.id(), target.id(), maximumBuffer, probe);
		if (args[4].equals("refine") || args[4].equals("gap"))
		{
			for (int round = 0; (round < 2) && outcome.hops().isEmpty(); round++)
			{
				final PhantomPathChainSearch.SearchOutcome currentOutcome = outcome;
				final List<PhantomPathChainSearch.Waypoint> frontier = points.stream().filter(p -> currentOutcome.settled().contains(p.id())).sorted(Comparator.comparingLong((PhantomPathChainSearch.Waypoint p) -> distanceSquared(target, p)).thenComparing(PhantomPathChainSearch.Waypoint::id)).limit(4).toList();
				for (PhantomPathChainSearch.Waypoint center : frontier)
				{
					addFrontier(points, factual, source, target, geo, center);
				}
				points.sort(Comparator.comparing(PhantomPathChainSearch.Waypoint::id));
				System.out.println("REFINE_ROUND " + round + " frontier=" + frontier.size() + " nodes=" + points.size());
				outcome = PhantomPathChainSearch.searchDetailed(points, source.id(), target.id(), maximumBuffer, probe);
			}
		}
		final List<PhantomPathChainSearch.Hop> chain = outcome.hops();
		final PhantomPathChainSearch.SearchOutcome finalOutcome = outcome;
		System.out.println("SEARCH_FRONTIER settled=" + outcome.settled().size() + " nearest=" + points.stream().filter(p -> finalOutcome.settled().contains(p.id())).sorted(Comparator.comparingLong((PhantomPathChainSearch.Waypoint p) -> distanceSquared(target, p)).thenComparing(PhantomPathChainSearch.Waypoint::id)).limit(8).map(p -> p.id() + ":" + p.point()).toList());
		final ArrayList<String> output = new ArrayList<>();
		output.add("sequence\tpoint_kind\tpoint_id\tx\ty\tz\tsource_refs\tnext_hop_distance\tnext_hop_required_buffer\tnext_hop_status\tnext_hop_path_length\tnext_hop_segments");
		for (int i = 0; i < chain.size(); i++)
		{
			final PhantomPathChainSearch.Hop hop = chain.get(i);
			output.add(String.join("\t", Integer.toString(i), hop.from().kind(), hop.from().id(), Integer.toString(hop.from().point().x()), Integer.toString(hop.from().point().y()), Integer.toString(hop.from().point().z()), hop.from().sourceRefs(), Long.toString((long) Math.ceil(Math.sqrt(distanceSquared(hop.from(), hop.to())))), Integer.toString(hop.requiredBuffer()), hop.proof().reason(), Long.toString(hop.proof().length()), Integer.toString(hop.proof().segments())));
		}
		if (!chain.isEmpty())
		{
			final PhantomPathChainSearch.Waypoint last = chain.getLast().to();
			output.add(String.join("\t", Integer.toString(chain.size()), last.kind(), last.id(), Integer.toString(last.point().x()), Integer.toString(last.point().y()), Integer.toString(last.point().z()), last.sourceRefs(), "0", "0", "END", "0", "0"));
		}
		Files.writeString(Path.of(args[3]), String.join("\n", output) + "\n", StandardCharsets.UTF_8);
		System.out.println("PATHCHAIN mode=" + args[4] + " candidates=" + points.size() + " hops=" + chain.size() + " max_buffer=" + maximumBuffer + " output=" + args[3]);
	}

	private static void addGrid(List<PhantomPathChainSearch.Waypoint> points, List<PhantomPathChainSearch.Waypoint> factual, PhantomPathChainSearch.Waypoint source, PhantomPathChainSearch.Waypoint target, GeoEngine geo, boolean refine)
	{
		final int minX = Math.min(source.point().x(), target.point().x()) - EXPANSION;
		final int maxX = Math.max(source.point().x(), target.point().x()) + EXPANSION;
		final int minY = Math.min(source.point().y(), target.point().y()) - EXPANSION;
		final int maxY = Math.max(source.point().y(), target.point().y()) + EXPANSION;
		for (int x = minX; x <= maxX; x += SPACING)
		{
			for (int y = minY; y <= maxY; y += SPACING)
			{
				addGridPoint(points, factual, source, target, geo, x, y);
			}
		}
		if (refine)
		{
			// The endpoints are the two disconnected frontiers in the failed 2048 probe.
			for (PhantomPathChainSearch.Waypoint endpoint : List.of(source, target))
			{
				for (int x = endpoint.point().x() - 4096; x <= endpoint.point().x() + 4096; x += 1024)
				{
					for (int y = endpoint.point().y() - 4096; y <= endpoint.point().y() + 4096; y += 1024)
					{
						addGridPoint(points, factual, source, target, geo, x, y);
					}
				}
			}
		}
	}

	private static void addGridPoint(List<PhantomPathChainSearch.Waypoint> points, List<PhantomPathChainSearch.Waypoint> factual, PhantomPathChainSearch.Waypoint source, PhantomPathChainSearch.Waypoint target, GeoEngine geo, int x, int y)
	{
		if (!geo.hasGeo(x, y))
		{
			return;
		}
		final PhantomPathChainSearch.Waypoint nearest = factual.stream().min(Comparator.comparingLong((PhantomPathChainSearch.Waypoint p) -> distanceSquared(x, y, p.point().x(), p.point().y())).thenComparing(PhantomPathChainSearch.Waypoint::id)).orElseThrow();
		final int z = geo.getHeight(x, y, nearest.point().z());
		if ((z != geo.getHeight(x, y, nearest.point().z())) || (z != geo.getHeight(x, y, z)))
		{
			return;
		}
		final int tileX = GeoEngine.getGeoX(x) / 2048;
		final int tileY = GeoEngine.getGeoY(y) / 2048;
		final String refs = "data/geodata/" + tileX + "_" + tileY + ".l2j;nearest:" + nearest.id();
		final String id = "route." + source.id() + "." + target.id() + "." + x + "." + y + "." + z;
		if (points.stream().noneMatch(p -> p.id().equals(id)))
		{
			points.add(new PhantomPathChainSearch.Waypoint(id, new PhantomGeoValidationRules.Point(x, y, z, 0), "ROUTE", refs));
		}
	}

	private static void addFrontier(List<PhantomPathChainSearch.Waypoint> points, List<PhantomPathChainSearch.Waypoint> factual, PhantomPathChainSearch.Waypoint source, PhantomPathChainSearch.Waypoint target, GeoEngine geo, PhantomPathChainSearch.Waypoint center)
	{
		final int minX = Math.min(source.point().x(), target.point().x()) - EXPANSION;
		final int maxX = Math.max(source.point().x(), target.point().x()) + EXPANSION;
		final int minY = Math.min(source.point().y(), target.point().y()) - EXPANSION;
		final int maxY = Math.max(source.point().y(), target.point().y()) + EXPANSION;
		for (int x = center.point().x() - 4096; x <= center.point().x() + 4096; x += 1024)
		{
			for (int y = center.point().y() - 4096; y <= center.point().y() + 4096; y += 1024)
			{
				if ((x >= minX) && (x <= maxX) && (y >= minY) && (y <= maxY))
				{
					addGridPoint(points, factual, source, target, geo, x, y);
				}
			}
		}
	}

	private static long distanceSquared(PhantomPathChainSearch.Waypoint a, PhantomPathChainSearch.Waypoint b)
	{
		return distanceSquared(a.point().x(), a.point().y(), b.point().x(), b.point().y());
	}

	private static long distanceSquared(int ax, int ay, int bx, int by)
	{
		final long dx = (long) ax - bx;
		final long dy = (long) ay - by;
		return (dx * dx) + (dy * dy);
	}
}
