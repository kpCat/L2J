package org.l2jmobius.gameserver.phantoms.navigation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.managers.ZoneManager;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.zone.ZoneType;
import org.l2jmobius.gameserver.model.zone.type.NoRestartZone;
import org.l2jmobius.gameserver.model.zone.type.SiegeZone;
import org.l2jmobius.gameserver.model.zone.type.WaterZone;
import org.l2jmobius.gameserver.phantoms.navigation.HumanRouteTrace.Point;

final class HumanRouteScenes
{
	private static final String HEADER = "PHANTOM_ROUTE_LEARNING\t1\t";
	private static final int MAX_SCENES = 8;
	private static final int MAX_SHARDS = 4;
	private static final int MAX_SHARD_BYTES = 2 * 1024 * 1024;
	static final String FARM_REFUSAL = "RUNTIME_FARM_REFUSAL";
	private static final int[][] EXITS = { { 0, 128 }, { 128, 0 }, { 0, -128 }, { -128, 0 } };

	record Scene(String id, Point start, Point target, int radius, int height, String startLandmark, String directions,
		String reason, long failures, FarmSource farmSource) { }
	record FarmSource(long profileId, int objectId) { }
	record Queue(List<Scene> ready, int needsLandmark) { }
	private record Edge(Point start, Point end) { }
	private record Failure(int geometry, int transientCount) { }

	static Queue read(HumanRouteMailbox mailbox, String fingerprint) throws IOException
	{
		return read(mailbox, mailbox.root(), fingerprint);
	}

	static Queue read(HumanRouteMailbox mailbox, Path bundle, String fingerprint) throws IOException
	{
		final String text = mailbox.input(bundle, "scenes.tsv", 8192);
		final List<Scene> result = new ArrayList<>();
		final Set<String> ids = new HashSet<>();
		final Set<Edge> used = new HashSet<>();
		final Map<String, Map<Edge, Failure>> shards = new HashMap<>();
		int needsLandmark = 0;
		for (String line : text.lines().toList())
		{
			final String[] fields = line.split("\t", -1);
			final boolean learned = fields.length == 12 && "LEARNED_FAILURE".equals(fields[11]);
			final boolean farm = fields.length == 15 && FARM_REFUSAL.equals(fields[11]);
			if ((!learned && !farm) || !fields[0].matches("[a-z0-9][a-z0-9-]{0,47}") || !ids.add(fields[0]) || ids.size() > MAX_SCENES)
			{
				throw new IOException("Invalid or duplicate route scene.");
			}
			final Point start = point(fields, 1);
			final Point target = point(fields, 4);
			final int radius = Integer.parseInt(fields[7]);
			final int height = Integer.parseInt(fields[8]);
			final Edge evidence = new Edge(start, target);
			if (radius < 16 || radius > 128 || height < 8 || height > 48 || start.within(target, radius + 32, height + 16) || !used.add(evidence))
			{
				throw new IOException("Invalid scene area or provenance.");
			}
			if (farm)
			{
				final var actor = new FarmSource(Long.parseLong(fields[12]), Integer.parseInt(fields[13]));
				if (actor.profileId() <= 0 || actor.objectId() <= 0 || !fingerprint.equals(fields[14]))
				{
					throw new IOException("Invalid runtime refusal source.");
				}
				if (!landmark(fields[9]) || !landmark(fields[10])) { needsLandmark++; continue; }
				result.add(new Scene(fields[0], start, target, radius, height, fields[9], fields[10], FARM_REFUSAL, 0, actor));
				continue;
			}
			final String source = "region-" + (start.x() >> 15) + "_" + (start.y() >> 15) + ".tsv";
			if (!shards.containsKey(source))
			{
				if (shards.size() == MAX_SHARDS) { throw new IOException("Too many source shards."); }
				shards.put(source, failures(mailbox.input(bundle, source, MAX_SHARD_BYTES), fingerprint));
			}
			final Failure failure = shards.get(source).get(evidence);
			if (failure == null) { throw new IOException("Scene has no matching failed directed edge."); }
			if (!landmark(fields[9]) || !landmark(fields[10]))
			{
				needsLandmark++;
				continue;
			}
			final String reason = failure.geometry() > 0 ? "GEOMETRY_FAILURE" : "TRANSIENT_ROUTE_FAILURE";
			result.add(new Scene(fields[0], start, target, radius, height, fields[9], fields[10], reason, (long) failure.geometry() + failure.transientCount(), null));
		}
		result.sort(Comparator.comparingLong(Scene::failures).reversed().thenComparing(Scene::id));
		return new Queue(List.copyOf(result), needsLandmark);
	}

	private static boolean landmark(String text)
	{
		return text.length() >= 5 && text.length() <= 180 && text.matches("[\\p{L}\\p{N} .,!?«»'()–-]+") && text.matches(".*[А-Яа-яЁё].*") && !text.matches(".*[0-9]{3,}.*");
	}

	private static Map<Edge, Failure> failures(String content, String fingerprint) throws IOException
	{
		final int newline = content.indexOf('\n');
		if (newline < 0 || !content.endsWith("\n") || !content.startsWith(HEADER + fingerprint + "\t"))
		{
			throw new IOException("Stale or unrecognised failure shard.");
		}
		final String payload = content.substring(newline + 1);
		if (!content.substring(0, newline).equals(HEADER + fingerprint + "\t" + sha256(payload)))
		{
			throw new IOException("Failure shard checksum mismatch.");
		}
		final Map<Edge, Failure> result = new HashMap<>();
		for (String line : payload.lines().toList())
		{
			final String[] fields = line.split("\t", -1);
			if (fields.length != 14) { throw new IOException("Invalid failure shard row."); }
			if (!"0".equals(fields[0]) || !"0".equals(fields[10])) { continue; }
			final int geometry = Integer.parseInt(fields[11]);
			final int transientCount = Integer.parseInt(fields[12]);
			if (geometry < 0 || transientCount < 0) { throw new IOException("Invalid failure counters."); }
			if (geometry == 0 && transientCount == 0) { continue; }
			if (!("FAILURE_ONLY".equals(fields[7]) && "FAILURE_ONLY".equals(fields[8]) && "0".equals(fields[9]))
				&& !("CANDIDATE".equals(fields[7]) && "GEODATA_PROPOSED".equals(fields[8]) && "1".equals(fields[9])))
			{
				throw new IOException("Failure provenance mismatch.");
			}
			final Edge edge = new Edge(point(fields, 1), point(fields, 4));
			if (result.putIfAbsent(edge, new Failure(geometry, transientCount)) != null) { throw new IOException("Duplicate failure row."); }
		}
		return result;
	}

	static Point point(String[] fields, int first)
	{
		return new Point(Integer.parseInt(fields[first]), Integer.parseInt(fields[first + 1]), Integer.parseInt(fields[first + 2]), 0);
	}

	static String sha256(String payload)
	{
		return HexFormat.of().formatHex(PhantomRouteLearning.sha256().digest(payload.getBytes(StandardCharsets.UTF_8)));
	}

	static Integer surface(Point point)
	{
		if (point.instance() != 0 || point.x() < World.WORLD_X_MIN || point.x() > World.WORLD_X_MAX || point.y() < World.WORLD_Y_MIN || point.y() > World.WORLD_Y_MAX)
		{
			return null;
		}
		final GeoEngine geo = GeoEngine.getInstance();
		return geo.hasGeoPos(GeoEngine.getGeoX(point.x()), GeoEngine.getGeoY(point.y())) ? geo.getHeight(point.x(), point.y(), point.z()) : null;
	}

	static boolean knownTarget(Point point, int height)
	{
		final Integer floor = surface(point);
		return floor != null && Math.abs((long) floor - point.z()) <= height && !restricted(point, false);
	}

	static boolean knownDryPoint(Point point, int height)
	{
		final Integer floor = surface(point);
		return floor != null && Math.abs((long) floor - point.z()) <= height && !restricted(point, true);
	}

	static boolean safeLanding(Point point, int tolerance)
	{
		final Integer floor = surface(point);
		if (floor == null || Math.abs((long) floor - point.z()) > tolerance || restricted(point, true) || restricted(new Point(point.x(), point.y(), floor, 0), true))
		{
			return false;
		}
		final var geo = GeoEngine.getInstance();
		int open = 0;
		for (int[] exit : EXITS)
		{
			boolean clear = true;
			for (int step = 1; step <= 4; step++)
			{
				final Point neighbour = new Point(point.x() + (exit[0] * step / 4), point.y() + (exit[1] * step / 4), floor, 0);
				final Integer neighbourFloor = surface(neighbour);
				if (neighbourFloor == null || Math.abs((long) neighbourFloor - floor) > 24 || restricted(neighbour, true)
					|| restricted(new Point(neighbour.x(), neighbour.y(), neighbourFloor, 0), true)
					|| !geo.canMoveToTarget(point.x(), point.y(), floor, neighbour.x(), neighbour.y(), neighbourFloor, 0)
					|| !geo.canMoveToTarget(neighbour.x(), neighbour.y(), neighbourFloor, point.x(), point.y(), floor, 0))
				{
					clear = false;
					break;
				}
			}
			if (clear && ++open >= 2) { return true; }
		}
		return false;
	}

	private static boolean restricted(Point point, boolean dry)
	{
		final var loaded = ZoneManager.getInstance().getLoadedZones(point.x(), point.y(), point.z());
		if (loaded.isEmpty()) { return true; }
		for (ZoneType zone : loaded.get())
		{
			if (!worldZone(zone.getInstanceId(), zone.getInstanceTemplateId()) || zone instanceof SiegeZone || zone instanceof NoRestartZone || (dry && zone instanceof WaterZone)) { return true; }
		}
		return false;
	}

	static boolean worldZone(int instanceId, int templateId)
	{
		return (instanceId == 0 || instanceId == -1) && templateId == -1;
	}
}
