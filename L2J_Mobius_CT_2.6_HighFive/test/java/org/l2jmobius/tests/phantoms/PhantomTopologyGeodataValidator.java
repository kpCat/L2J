package org.l2jmobius.tests.phantoms;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Element;

import org.l2jmobius.gameserver.config.GeoEngineConfig;
import org.l2jmobius.gameserver.data.xml.FenceData;
import org.l2jmobius.gameserver.data.xml.MapRegionData;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.geoengine.pathfinding.GeoLocation;
import org.l2jmobius.gameserver.geoengine.pathfinding.PathFinding;
import org.l2jmobius.gameserver.phantoms.background.L2jPhantomBackgroundAuthority;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchor;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchorRole;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyArea;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint;

/** Sequential GeoEngine proof for the immutable LIVE-002-B candidate tables. */
public final class PhantomTopologyGeodataValidator implements PhantomTestSuite
{
	private static final int MAXIMUM_ANCHOR_XY = 32;
	private static final String CLOSED_SOURCE = "^data/spawns/(Catacombs/.*|(Aden/TowerOfInsolence|Giran/DevilsIsle|Goddard/ImperialTomb|Oren/IvoryTower)\\.xml)$";
	private static final String ANCHOR_HEADER = "coverage_key\tnode_id\tanchor_id\tstatus\tx\ty\tz\tattempts\tgeometry_kind\tmap_region_loc_id\tsource_refs\n";
	private static final String ROUTE_HEADER = "route_id\tdirection\tfrom_node_id\tto_node_id\tfrom_anchor_id\tto_anchor_id\tstatus\tlength\tsegments\n";
	private static final List<String> RUINS_KEYS = List.of(
		"b729b4b54a41170f2da2aa85307ff726b04e894296e26e9d3d85be8899dbd4f3",
		"38437b55476d236dac633021da7467aec32ccb5971a087c0d7364c0364b71f2a",
		"a993df05ccc2b7aacfce6b420e50459a29da86cb9c499e6996e426c60a7e8a55",
		"e5f729fdaee258fc47cd7e7d98f3deaa401842fb9448589958eae93b1663a44d",
		"71ca298b5ca60e08e7df1bc9cde9a00f004be5bd9cbce5f117aa0c5e1b3467fe",
		"a569d06074b5842ba1ba13c18b41ddd16c267b2f4d5f81178491272f6ce0057b",
		"286654e4ee4ce4c034fbee2f2535a788803c0b549649e3d0b63f2ca99367b842",
		"61295e4685dc1ed6dd4c6cd6181cdab14ca6353f81278d2c8e2376a690a307b9",
		"6b0c3b8a8b12f57e49ffddee812bee7c88aa50e2162b384cb9c237447b4728d2",
		"29e338532c483e5b6e97557dfbdd9e980987f639d5e6c7bc5a28dd6673389f19");
	private PhantomHeadlessPlayerTestEnvironment _environment;

	@Override
	public String id()
	{
		return "topology-geodata-validation";
	}

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		_environment = new PhantomHeadlessPlayerTestEnvironment();
		_environment.initialize(context);
		FenceData.getInstance();
	}

	@Override
	public void afterAll(PhantomTestContext context) throws Exception
	{
		if (_environment != null)
		{
			_environment.shutdown();
		}
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-all-candidates", _ -> validate(new String[] {System.getProperty("phantom.geodata.input"), System.getProperty("phantom.geodata.core"), System.getProperty("phantom.geodata.output")}));
	}

	record Row(Map<String, String> values)
	{
		String get(String key)
		{
			return values.getOrDefault(key, "");
		}
	}

	private record Anchor(String nodeId, String anchorId, PhantomGeoValidationRules.Point point)
	{
	}

	private record Geometry(PhantomTopologyArea area, List<PhantomGeoValidationRules.Point> candidates, int minimumZ, int maximumZ)
	{
	}

	private static void validate(String[] args) throws Exception
	{
		if (args.length != 3)
		{
			throw new IllegalArgumentException("Usage: candidate-directory core-xml output-directory");
		}
		final Path candidateDirectory = Path.of(args[0]);
		final Path coreXml = Path.of(args[1]);
		final Path outputDirectory = Path.of(args[2]);
		Files.createDirectories(outputDirectory);
		if (GeoEngineConfig.PATHFINDING != 2)
		{
			throw new IllegalStateException("PathFinding=2 is required for this proof.");
		}
		final GeoEngine geo = GeoEngine.getInstance();
		final PathFinding pathFinding = PathFinding.getInstance();
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
				final List<GeoLocation> path = pathFinding.findPath(from.x(), from.y(), from.z(), to.x(), to.y(), to.z(), from.instanceId(), true);
				if (path == null)
				{
					return null;
				}
				return path.stream().map(point -> new PhantomGeoValidationRules.Point(point.getX(), point.getY(), point.getZ(), 0)).toList();
			}
		};
		final Map<String, Anchor> validAnchors = coreAnchors(coreXml, probe);
		validAnchors.putAll(coreAnchors(coreXml.resolveSibling("high-five-siege.xml"), probe));
		final List<Row> candidates = rows(candidateDirectory.resolve("TOPOLOGY_CANDIDATES.tsv"));
		final Map<String, Integer> geometryMultiplicity = new HashMap<>();
		for (Row row : candidates)
		{
			if (row.get("ownership").equals("GENERATED_CANDIDATE") && !row.get("geometry_kind").equals("POINT"))
			{
				geometryMultiplicity.merge(row.get("source_refs") + "|" + row.get("source_geometry").split(";", 2)[0], 1, Integer::sum);
			}
		}
		final StringBuilder anchors = new StringBuilder(ANCHOR_HEADER);
		final ArrayList<Integer> attempts = new ArrayList<>();
		int generated = 0;
		int valid = 0;
		for (Row row : candidates)
		{
			if (!row.get("ownership").equals("GENERATED_CANDIDATE"))
			{
				continue;
			}
			generated++;
			String status = "NO_CANDIDATE";
			PhantomGeoValidationRules.Point accepted = null;
			int used = 0;
			try
			{
				if (Integer.parseInt(row.get("instance_id")) != 0)
				{
					status = "CROSS_INSTANCE";
				}
				else if (row.get("geometry_kind").equals("POINT"))
				{
					status = "UNSUPPORTED_POINT_AREA";
				}
				else if (row.get("source_refs").matches(CLOSED_SOURCE))
				{
					status = "CLOSED_SOURCE_NO_DOOR_PATH";
				}
				else if (geometryMultiplicity.getOrDefault(row.get("source_refs") + "|" + row.get("source_geometry").split(";", 2)[0], 0) > 1)
				{
					status = "AMBIGUOUS_GEOMETRY";
				}
				else
				{
					final Geometry geometry = geometry(row);
					for (PhantomGeoValidationRules.Point candidate : geometry.candidates())
					{
						used++;
						if (!geometry.area().contains(new PhantomTopologyPoint(candidate.x(), candidate.y(), candidate.z(), 0)))
						{
							status = "OUTSIDE_GEOMETRY";
							continue;
						}
						final List<PhantomGeoValidationRules.Point> local = localPoints(candidate, geometry.area(), probe);
						final int zHint = candidate.z();
						final int allowedZ = Math.max(Math.abs(zHint - geometry.minimumZ()), Math.abs(zHint - geometry.maximumZ()));
						final PhantomGeoValidationRules.AnchorProof proof = PhantomGeoValidationRules.anchor(candidate, local, zHint, allowedZ, probe);
						status = proof.reason();
						if (!status.equals("VALID"))
						{
							continue;
						}
						if (!geometry.area().contains(new PhantomTopologyPoint(proof.point().x(), proof.point().y(), proof.point().z(), 0)))
						{
							status = "OUTSIDE_GEOMETRY";
							continue;
						}
						final List<PhantomGeoValidationRules.Point> vertices = geometry.area().vertices().stream().map(vertex -> new PhantomGeoValidationRules.Point(vertex.x(), vertex.y(), proof.point().z(), 0)).toList();
						if (!PhantomGeoValidationRules.withinTargetDistance(proof.point(), vertices, 2000))
						{
							status = "PLANNER_TARGET_DISTANCE";
							continue;
						}
						if (!row.get("map_region_loc_id").isEmpty() && (MapRegionData.getInstance().getMapRegionLocId(proof.point().x(), proof.point().y()) != Integer.parseInt(row.get("map_region_loc_id"))))
						{
							status = "MAP_REGION_MISMATCH";
							continue;
						}
						final PhantomTopologyAnchor topologyAnchor = new PhantomTopologyAnchor(row.get("anchor_id"), PhantomTopologyAnchorRole.FARMING, row.get("node_id"), new PhantomTopologyPoint(proof.point().x(), proof.point().y(), proof.point().z(), 0), null, row.get("map_region_loc_id").isEmpty() ? null : Integer.parseInt(row.get("map_region_loc_id")), 0, List.of("outdoor-farming"), List.of(row.get("source_refs")));
						if (L2jPhantomBackgroundAuthority.canonicalCommittedAnchorPosition(topologyAnchor, 0).isEmpty())
						{
							status = "CANONICAL_REJECTED";
							continue;
						}
						accepted = proof.point();
						break;
					}
				}
			}
			catch (IllegalArgumentException exception)
			{
				status = "INVALID_SOURCE_GEOMETRY";
			}
			attempts.add(used);
			if (accepted != null)
			{
				valid++;
				validAnchors.put(row.get("node_id"), new Anchor(row.get("node_id"), row.get("anchor_id"), accepted));
			}
			anchors.append(tsv(row.get("coverage_key"), row.get("node_id"), row.get("anchor_id"), status, accepted == null ? "" : Integer.toString(accepted.x()), accepted == null ? "" : Integer.toString(accepted.y()), accepted == null ? "" : Integer.toString(accepted.z()), Integer.toString(used), row.get("geometry_kind"), row.get("map_region_loc_id"), row.get("source_refs")));
			if ((generated % 500) == 0)
			{
				System.out.println("anchor-progress " + generated + "/2632 valid=" + valid);
			}
		}
		Files.writeString(outputDirectory.resolve("ANCHOR_GEODATA_VALIDATION.tsv"), anchors.toString(), StandardCharsets.UTF_8);
		final StringBuilder routes = new StringBuilder(ROUTE_HEADER);
		int directions = 0;
		int evidenceRows = 0;
		int routeValid = 0;
		for (Row route : rows(candidateDirectory.resolve("ROUTE_CANDIDATES.tsv")))
		{
			if (!List.of("LOCAL_WALK", "REGION_LINK").contains(route.get("candidate_mode")))
			{
				final String status = route.get("candidate_mode").equals("TELEPORT_FACT") ? "TELEPORT_EVIDENCE_ONLY" : "EXISTING_CORE_PRESERVED";
				routes.append(tsv(route.get("route_id"), "E", route.get("from_id"), route.get("to_id"), "", "", status, "0", "0"));
				evidenceRows++;
				continue;
			}
			for (int direction = 0; direction < 2; direction++)
			{
				final String fromNode = direction == 0 ? route.get("from_id") : route.get("to_id");
				final String toNode = direction == 0 ? route.get("to_id") : route.get("from_id");
				final Anchor from = validAnchors.get(fromNode);
				final Anchor to = validAnchors.get(toNode);
				final PhantomGeoValidationRules.RouteProof proof = (from == null) || (to == null) ? new PhantomGeoValidationRules.RouteProof("ENDPOINT_BLOCKED", 0, 0) : PhantomGeoValidationRules.route(from.point(), to.point(), probe);
				if (proof.reason().startsWith("VALID"))
				{
					routeValid++;
				}
				routes.append(tsv(route.get("route_id"), direction == 0 ? "F" : "R", fromNode, toNode, from == null ? "" : from.anchorId(), to == null ? "" : to.anchorId(), proof.reason(), Long.toString(proof.length()), Integer.toString(proof.segments())));
				directions++;
			}
			if ((directions % 1000) == 0)
			{
				System.out.println("route-progress " + directions + "/7100 valid=" + routeValid);
			}
		}
		Files.writeString(outputDirectory.resolve("ROUTE_GEODATA_VALIDATION.tsv"), routes.toString(), StandardCharsets.UTF_8);
		final Map<String, Row> candidatesByKey = new HashMap<>();
		for (Row row : candidates)
		{
			candidatesByKey.put(row.get("coverage_key"), row);
		}
		final boolean landmarkGeo = probe.hasGeo(-19120, 136816);
		final int landmarkZ = landmarkGeo ? probe.height(-19120, 136816, -3752) : -3752;
		final boolean landmarkStable = landmarkGeo && (landmarkZ == probe.height(-19120, 136816, -3752)) && (landmarkZ == probe.height(-19120, 136816, landmarkZ)) && (Math.abs((long) landmarkZ + 3752) <= 100);
		final StringBuilder ruins = new StringBuilder("coverage_key\tanchor_status\tlandmark_status\tpath_status\tlength\tsegments\n");
		for (String key : Boolean.getBoolean("phantom.geodata.fixture") ? List.<String>of() : RUINS_KEYS)
		{
			final Row candidate = candidatesByKey.get(key);
			if (candidate == null)
			{
				throw new IllegalStateException("Accepted Ruins proximity candidate is missing: " + key);
			}
			final Anchor anchor = validAnchors.get(candidate.get("node_id"));
			final String pathStatus;
			long length = 0;
			int segments = 0;
			if (!landmarkStable)
			{
				pathStatus = "LANDMARK_BLOCKED";
			}
			else if (anchor == null)
			{
				pathStatus = "ANCHOR_BLOCKED";
			}
			else
			{
				final PhantomGeoValidationRules.RouteProof proof = PhantomGeoValidationRules.route(new PhantomGeoValidationRules.Point(-19120, 136816, landmarkZ, 0), anchor.point(), probe);
				pathStatus = proof.reason();
				length = proof.length();
				segments = proof.segments();
			}
			ruins.append(tsv(key, anchor == null ? "BLOCKED" : "VALID", landmarkStable ? "VALID" : "NO_GEODATA_OR_Z_DRIFT", pathStatus, Long.toString(length), Integer.toString(segments)));
		}
		Files.writeString(outputDirectory.resolve("RUINS_GEODATA_VALIDATION.tsv"), ruins.toString(), StandardCharsets.UTF_8);
		attempts.sort(Integer::compareTo);
		System.out.println("GEO_VALIDATION generated=" + generated + " valid_anchors=" + valid + " route_directions=" + directions + " valid_routes=" + routeValid + " evidence_only=" + evidenceRows + " max_attempts=" + attempts.getLast() + " p95_attempts=" + attempts.get((int) Math.ceil(attempts.size() * 0.95) - 1));
		if (!Boolean.getBoolean("phantom.geodata.fixture") && ((generated != 2632) || (directions != 7100) || (evidenceRows != 1557)))
		{
			throw new IllegalStateException("Immutable B candidate accounting changed.");
		}
	}

	private static Map<String, Anchor> coreAnchors(Path coreXml, PhantomGeoValidationRules.Probe probe) throws Exception
	{
		final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		final Element root = factory.newDocumentBuilder().parse(coreXml.toFile()).getDocumentElement();
		final Map<String, Anchor> anchors = new HashMap<>();
		int checked = 0;
		for (int i = 0; i < root.getChildNodes().getLength(); i++)
		{
			if (!(root.getChildNodes().item(i) instanceof Element element) || !element.getTagName().equals("anchor"))
			{
				continue;
			}
			checked++;
			final int x = Integer.parseInt(element.getAttribute("x"));
			final int y = Integer.parseInt(element.getAttribute("y"));
			final int z = Integer.parseInt(element.getAttribute("z"));
			final int instanceId = Integer.parseInt(element.getAttribute("instanceId"));
			if ((instanceId != 0) || !probe.hasGeo(x, y))
			{
				continue;
			}
			final int normalized = probe.height(x, y, z);
			final int tolerance = Integer.parseInt(element.getAttribute("tolerance"));
			if ((normalized != probe.height(x, y, z)) || (normalized != probe.height(x, y, normalized)) || (Math.abs((long) normalized - z) > tolerance))
			{
				continue;
			}
			final String nodeId = element.getAttribute("nodeId");
			final String id = element.getAttribute("id");
			final PhantomTopologyAnchor anchor = new PhantomTopologyAnchor(id, PhantomTopologyAnchorRole.valueOf(element.getAttribute("role")), nodeId, new PhantomTopologyPoint(x, y, z, instanceId), element.hasAttribute("npcId") ? Integer.parseInt(element.getAttribute("npcId")) : null, element.hasAttribute("mapRegionLocId") ? Integer.parseInt(element.getAttribute("mapRegionLocId")) : null, tolerance, List.of(), List.of());
			if (L2jPhantomBackgroundAuthority.canonicalCommittedAnchorPosition(anchor, 0).isPresent())
			{
				anchors.putIfAbsent(nodeId, new Anchor(nodeId, id, new PhantomGeoValidationRules.Point(x, y, normalized, 0)));
			}
		}
		System.out.println("core-geodata checked=" + checked + " usable_nodes=" + anchors.size());
		return anchors;
	}

	private static Geometry geometry(Row row)
	{
		final String territory = row.get("source_geometry").split(";", 2)[0];
		final String[] parts = territory.split(":", 4);
		if ((parts.length != 4) || !parts[0].equals("territory"))
		{
			throw new IllegalArgumentException("Missing factual polygon.");
		}
		final int minZ = Integer.parseInt(parts[1]);
		final int maxZ = Integer.parseInt(parts[2]);
		final int midpoint = minZ + ((maxZ - minZ) / 2);
		final List<PhantomTopologyArea.Vertex> vertices = Arrays.stream(parts[3].split("\\|")).map(value ->
		{
			final String[] xy = value.split(",");
			return new PhantomTopologyArea.Vertex(Integer.parseInt(xy[0]), Integer.parseInt(xy[1]));
		}).toList();
		final PhantomTopologyArea area = PhantomTopologyArea.polygon(0, minZ, maxZ, vertices);
		final LinkedHashMap<String, PhantomGeoValidationRules.Point> candidates = new LinkedHashMap<>();
		if (!row.get("anchor_x").isEmpty() && !row.get("anchor_y").isEmpty())
		{
			add(candidates, Integer.parseInt(row.get("anchor_x")), Integer.parseInt(row.get("anchor_y")), row.get("anchor_z").isEmpty() ? midpoint : Integer.parseInt(row.get("anchor_z")));
		}
		if (row.get("source_geometry").contains(";points="))
		{
			for (String xyz : row.get("source_geometry").split(";points=", 2)[1].split("\\|"))
			{
				final String[] values = xyz.split(",");
				add(candidates, Integer.parseInt(values[0]), Integer.parseInt(values[1]), Integer.parseInt(values[2]));
			}
		}
		final long cx = vertices.stream().mapToLong(PhantomTopologyArea.Vertex::x).sum() / vertices.size();
		final long cy = vertices.stream().mapToLong(PhantomTopologyArea.Vertex::y).sum() / vertices.size();
		add(candidates, (int) cx, (int) cy, midpoint);
		for (PhantomTopologyArea.Vertex vertex : vertices)
		{
			add(candidates, vertex.x(), vertex.y(), midpoint);
			add(candidates, (int) ((cx + vertex.x()) / 2), (int) ((cy + vertex.y()) / 2), midpoint);
		}
		for (int gx = 1; gx <= 4; gx++)
		{
			for (int gy = 1; gy <= 4; gy++)
			{
				add(candidates, area.minX() + ((area.maxX() - area.minX()) * gx / 5), area.minY() + ((area.maxY() - area.minY()) * gy / 5), midpoint);
			}
		}
		return new Geometry(area, candidates.values().stream().limit(MAXIMUM_ANCHOR_XY).toList(), minZ, maxZ);
	}

	private static void add(Map<String, PhantomGeoValidationRules.Point> points, int x, int y, int z)
	{
		points.putIfAbsent(x + "," + y, new PhantomGeoValidationRules.Point(x, y, z, 0));
	}

	private static List<PhantomGeoValidationRules.Point> localPoints(PhantomGeoValidationRules.Point candidate, PhantomTopologyArea area, PhantomGeoValidationRules.Probe probe)
	{
		final ArrayList<PhantomGeoValidationRules.Point> result = new ArrayList<>();
		for (int distance : List.of(32, 64, 96))
		{
			for (int[] offset : new int[][] {{distance, 0}, {-distance, 0}, {0, distance}, {0, -distance}, {distance, distance}, {-distance, -distance}})
			{
				final int x = candidate.x() + offset[0];
				final int y = candidate.y() + offset[1];
				if (probe.hasGeo(x, y))
				{
					final int z = probe.height(x, y, candidate.z());
					final PhantomTopologyPoint point = new PhantomTopologyPoint(x, y, z, 0);
					if (area.contains(point))
					{
						result.add(new PhantomGeoValidationRules.Point(x, y, z, 0));
					}
				}
			}
		}
		return result;
	}

	static List<Row> rows(Path file) throws Exception
	{
		final List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
		final String[] columns = lines.getFirst().split("\\t", -1);
		final ArrayList<Row> rows = new ArrayList<>();
		for (int line = 1; line < lines.size(); line++)
		{
			final String[] values = lines.get(line).split("\\t", -1);
			if (values.length != columns.length)
			{
				throw new IllegalArgumentException("Malformed TSV row " + line + " in " + file);
			}
			final Map<String, String> row = new HashMap<>();
			for (int column = 0; column < columns.length; column++)
			{
				row.put(columns[column], values[column]);
			}
			rows.add(new Row(row));
		}
		return rows;
	}

	private static String tsv(String... values)
	{
		return String.join("\t", values) + "\n";
	}
}
