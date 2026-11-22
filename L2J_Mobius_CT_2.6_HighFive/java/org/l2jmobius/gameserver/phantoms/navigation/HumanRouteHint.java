package org.l2jmobius.gameserver.phantoms.navigation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.phantoms.navigation.HumanRouteTrace.Point;

/** Explicit read-only adapter. This type cannot supply a native arrival receipt. */
public final class HumanRouteHint
{
	private static final int MAX_BYTES = 512 * 1024;
	private final UUID _run;
	private final String _scene;
	private final List<PhantomNavigationPoint> _waypoints;

	private HumanRouteHint(UUID run, String scene, List<PhantomNavigationPoint> waypoints)
	{
		_run = run;
		_scene = scene;
		_waypoints = List.copyOf(waypoints);
	}

	public UUID run() { return _run; }
	public String scene() { return _scene; }
	public List<PhantomNavigationPoint> waypoints() { return _waypoints; }

	public static Optional<HumanRouteHint> readWalkCandidate(Path sealedExport, String currentGeoDataFingerprint) throws IOException
	{
		if (!sealedExport.getFileName().toString().endsWith(".trace") || !Files.isRegularFile(sealedExport, LinkOption.NOFOLLOW_LINKS))
		{
			return Optional.empty();
		}
		final String content;
		try (var input = Files.newInputStream(sealedExport, LinkOption.NOFOLLOW_LINKS))
		{
			final byte[] bytes = input.readNBytes(MAX_BYTES + 1);
			if (bytes.length > MAX_BYTES) { return Optional.empty(); }
			content = new String(bytes, StandardCharsets.UTF_8);
		}
		final int newline = content.indexOf('\n');
		if (newline < 0) { return Optional.empty(); }
		final String body = content.substring(newline + 1);
		if (!content.substring(0, newline).equals("sha256=" + HumanRouteScenes.sha256(body))) { return Optional.empty(); }
		try
		{
			return parse(body, currentGeoDataFingerprint);
		}
		catch (IllegalArgumentException invalid)
		{
			return Optional.empty();
		}
	}

	private static Optional<HumanRouteHint> parse(String body, String fingerprint)
	{
		final Map<String, String> metadata = new HashMap<>();
		final List<Point> raw = new ArrayList<>();
		for (String line : body.lines().toList())
		{
			if (line.startsWith("SAMPLE\t"))
			{
				final String[] fields = line.split("\t", -1);
				if (fields.length != 9 || !"0".equals(fields[3]) || !"false".equals(fields[6]) || "UNKNOWN".equals(fields[5]) || raw.size() == HumanRouteTrace.MAX_SAMPLES
					|| Long.parseLong(fields[1]) != raw.size() + 1)
				{
					return Optional.empty();
				}
				raw.add(point(fields[4]));
				continue;
			}
			final int equals = line.indexOf('=');
			if (!raw.isEmpty() || equals < 1 || metadata.putIfAbsent(line.substring(0, equals), line.substring(equals + 1)) != null) { return Optional.empty(); }
		}
		final String scene = metadata.getOrDefault("scene", "");
		if (fingerprint == null || !fingerprint.matches("[0-9a-f]{64}") || !fingerprint.equals(metadata.get("geodata")) || !"1".equals(metadata.get("schema"))
			|| !"HUMAN_DEMONSTRATED".equals(metadata.get("type")) || !"ARRIVED".equals(metadata.get("result")) || !"0".equals(metadata.get("drops"))
			|| !"0".equals(metadata.get("breaks")) || !"false".equals(metadata.get("water")) || !Integer.toString(raw.size()).equals(metadata.get("samples"))
			|| raw.size() < 2 || !scene.matches("[a-z0-9][a-z0-9-]{0,47}"))
		{
			return Optional.empty();
		}
		final UUID run = UUID.fromString(metadata.getOrDefault("run", ""));
		final Point actualStart = point(metadata.getOrDefault("start", ""));
		final Point actualEnd = point(metadata.getOrDefault("end", ""));
		final Point target = point(metadata.getOrDefault("target", ""));
		final int radius = Integer.parseInt(metadata.getOrDefault("area_radius", "0"));
		final int height = Integer.parseInt(metadata.getOrDefault("area_height", "0"));
		if (radius < 16 || radius > 128 || height < 8 || height > 48 || actualStart.within(target, radius, height) || !actualEnd.within(target, radius, height))
		{
			return Optional.empty();
		}
		raw.addFirst(actualStart);
		raw.addLast(actualEnd);
		final List<PhantomNavigationPoint> candidate = new ArrayList<>();
		Point previous = null;
		for (Point point : raw)
		{
			if (point.equals(previous)) { continue; }
			if (!HumanRouteScenes.knownDryPoint(point, 24) || (previous != null && (!point.within(previous, 250, 96)
				|| !GeoEngine.getInstance().canMoveToTarget(previous.x(), previous.y(), previous.z(), point.x(), point.y(), point.z(), 0))))
			{
				return Optional.empty();
			}
			candidate.add(new PhantomNavigationPoint(point.x(), point.y(), point.z(), 0));
			previous = point;
		}
		return candidate.size() < 2 ? Optional.empty() : Optional.of(new HumanRouteHint(run, scene, candidate));
	}

	private static Point point(String encoded)
	{
		final String[] values = encoded.split(",", -1);
		if (values.length != 4 || !"0".equals(values[3])) { throw new IllegalArgumentException("Invalid walk point."); }
		return HumanRouteScenes.point(values, 0);
	}
}
