package org.l2jmobius.tests.phantoms;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Element;

import org.l2jmobius.tests.phantoms.PhantomTopologyGeodataValidator.Row;

/** Deterministic, fail-closed promotion of GeoEngine proof rows to topology XML. */
public final class PhantomTopologyGeodataPublisher
{
	private static final int DATASET_VERSION = 4;
	private static final int SHARD_BYTES = 3_800_000;
	private static final String ROOT = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<topology schemaVersion=\"1\" datasetId=\"high-five-core\" datasetVersion=\"4\">\n";
	private static final String END = "</topology>\n";
	private static final Map<String, String> ACCEPTED = Map.of(
		"WORLD_COVERAGE.tsv", "84af90619ff959af64119ad079cf0f2b85692dec2aa985d2314185b059c76c8e",
		"WORLD_DATA_MANIFEST.json", "94ac62cbe005e6df43529506fbb7af6511525a8d3bad355e66413583788a6bf4",
		"WORLD_SPATIAL_ASSOCIATIONS.tsv", "8ce940eeba2285ced494b5880be5f624eb8eba91a4ed24b5c7d5688e15600935",
		"TOPOLOGY_CANDIDATES.tsv", "aec029b27e0f9e8f04a86afc16b3c1a5e7e6ddbaedc7a37f25b0b2f08b7c873f",
		"ROUTE_CANDIDATES.tsv", "b757c0690c4890f5d3464cc921897087841261766f560b838f90a80f5a829bfa",
		"TOPOLOGY_CANDIDATE_MANIFEST.json", "d5ebd388bf59a9ee24c59d8bfe801c72c0f2783a2c92c49f4b0a1d5948039a6a");
	private static final int[][] BANDS = {{1, 5}, {6, 10}, {11, 19}, {20, 39}, {40, 51}, {52, 60}, {61, 75}, {76, 80}, {81, 85}};

	public static void main(String[] args) throws Exception
	{
		if (args.length != 4)
		{
			throw new IllegalArgumentException("Usage: candidate-directory validation-directory core-xml output-directory");
		}
		final Path candidates = Path.of(args[0]);
		final Path validation = Path.of(args[1]);
		final Path coreXml = Path.of(args[2]);
		final Path output = Path.of(args[3]);
		for (Map.Entry<String, String> accepted : ACCEPTED.entrySet())
		{
			if (!sha256(candidates.resolve(accepted.getKey())).equals(accepted.getValue()))
			{
				throw new IllegalStateException("Accepted input drift: " + accepted.getKey());
			}
		}
		Files.createDirectories(output);
		final List<Row> candidateNodes = PhantomTopologyGeodataValidator.rows(candidates.resolve("TOPOLOGY_CANDIDATES.tsv"));
		final List<Row> candidateRoutes = PhantomTopologyGeodataValidator.rows(candidates.resolve("ROUTE_CANDIDATES.tsv"));
		final List<Row> anchorProofs = PhantomTopologyGeodataValidator.rows(validation.resolve("ANCHOR_GEODATA_VALIDATION.tsv"));
		final List<Row> routeProofs = PhantomTopologyGeodataValidator.rows(validation.resolve("ROUTE_GEODATA_VALIDATION.tsv"));
		final Map<String, Row> anchorsByKey = unique(anchorProofs, "coverage_key");
		if (anchorsByKey.size() != 2632)
		{
			throw new IllegalStateException("Every generated anchor must have exactly one proof row.");
		}
		final Map<String, Row> routesByKey = new HashMap<>();
		for (Row row : routeProofs)
		{
			final String key = row.get("route_id") + ":" + row.get("direction");
			if (routesByKey.putIfAbsent(key, row) != null)
			{
				throw new IllegalStateException("Duplicate directed route proof: " + key);
			}
		}
		if ((routesByKey.size() != 8657) || (routeProofs.stream().filter(row -> row.get("direction").equals("E")).count() != 1557))
		{
			throw new IllegalStateException("Every walking direction and evidence-only candidate must have one proof row.");
		}
		final ArrayList<String> entities = new ArrayList<>();
		final Set<String> publishedAnchors = new HashSet<>();
		final Map<String, Row> generatedByKey = new LinkedHashMap<>();
		int generatedNodes = 0;
		for (Row candidate : candidateNodes)
		{
			if (!candidate.get("ownership").equals("GENERATED_CANDIDATE"))
			{
				continue;
			}
			generatedByKey.put(candidate.get("coverage_key"), candidate);
			final Row proof = anchorsByKey.get(candidate.get("coverage_key"));
			if ((proof == null) || !proof.get("node_id").equals(candidate.get("node_id")) || !proof.get("anchor_id").equals(candidate.get("anchor_id")))
			{
				throw new IllegalStateException("Missing or mismatched anchor proof: " + candidate.get("coverage_key"));
			}
			if (!proof.get("status").equals("VALID"))
			{
				continue;
			}
			entities.add(nodeXml(candidate));
			entities.add(anchorXml(candidate, proof));
			publishedAnchors.add(proof.get("anchor_id"));
			generatedNodes++;
		}
		if (generatedByKey.size() != 2632)
		{
			throw new IllegalStateException("Generated candidate accounting drift.");
		}
		final Map<String, String> coreAnchors = coreAnchors(coreXml);
		coreAnchors.putAll(coreAnchors(coreXml.resolveSibling("high-five-siege.xml")));
		final Map<String, List<String>> graph = coreGraph(coreXml);
		coreGraph(coreXml.resolveSibling("high-five-siege.xml")).forEach((key, value) -> graph.computeIfAbsent(key, _ -> new ArrayList<>()).addAll(value));
		int publishedEdges = 0;
		for (Row candidate : candidateRoutes)
		{
			if (!List.of("LOCAL_WALK", "REGION_LINK").contains(candidate.get("candidate_mode")))
			{
				continue;
			}
			for (String direction : List.of("F", "R"))
			{
				final Row proof = routesByKey.get(candidate.get("route_id") + ":" + direction);
				if (proof == null)
				{
					throw new IllegalStateException("Missing directed proof: " + candidate.get("route_id"));
				}
				if (!proof.get("status").startsWith("VALID_"))
				{
					continue;
				}
				if ((Long.parseLong(proof.get("length")) <= 0) || proof.get("from_anchor_id").isEmpty() || proof.get("to_anchor_id").isEmpty())
				{
					throw new IllegalStateException("Invalid accepted route proof: " + candidate.get("route_id"));
				}
				if (!publishedAnchors.contains(proof.get("from_anchor_id")) && !coreAnchors.containsKey(proof.get("from_anchor_id")))
				{
					throw new IllegalStateException("Unpublished source anchor: " + candidate.get("route_id"));
				}
				if (!publishedAnchors.contains(proof.get("to_anchor_id")) && !coreAnchors.containsKey(proof.get("to_anchor_id")))
				{
					throw new IllegalStateException("Unpublished target anchor: " + candidate.get("route_id"));
				}
				if (!proof.get("from_node_id").equals(direction.equals("F") ? candidate.get("from_id") : candidate.get("to_id")) || !proof.get("to_node_id").equals(direction.equals("F") ? candidate.get("to_id") : candidate.get("from_id")))
				{
					throw new IllegalStateException("Route direction mismatch: " + candidate.get("route_id"));
				}
				entities.add(edgeXml(candidate, proof));
				graph.computeIfAbsent(proof.get("from_anchor_id"), _ -> new ArrayList<>()).add(proof.get("to_anchor_id"));
				publishedEdges++;
			}
		}
		final Set<String> reachable = reachable(graph, coreAnchors.keySet());
		final List<String> shards = writeShards(output, entities);
		final Map<String, String> hashes = new LinkedHashMap<>();
		for (String shard : shards)
		{
			hashes.put(shard, sha256(output.resolve(shard)));
		}
		final Map<String, Integer> ruins = ruins(validation, generatedByKey, anchorsByKey, reachable);
		final StringBuilder manifest = new StringBuilder();
		manifest.append("{\n  \"schema\": \"LIVE-002-C/1\",\n  \"datasetId\": \"high-five-core\",\n  \"datasetVersion\": ").append(DATASET_VERSION).append(",\n");
		manifest.append("  \"anchorValidationSha256\": \"").append(sha256(validation.resolve("ANCHOR_GEODATA_VALIDATION.tsv"))).append("\",\n");
		manifest.append("  \"routeValidationSha256\": \"").append(sha256(validation.resolve("ROUTE_GEODATA_VALIDATION.tsv"))).append("\",\n");
		manifest.append("  \"ruinsValidationSha256\": \"").append(sha256(validation.resolve("RUINS_GEODATA_VALIDATION.tsv"))).append("\",\n");
		manifest.append("  \"generatedNodes\": ").append(generatedNodes).append(",\n  \"generatedEdges\": ").append(publishedEdges).append(",\n");
		manifest.append("  \"travelPolicy\": \"ceil(validated path length / 100 world units per second), minimum 1000 ms\",\n");
		manifest.append("  \"shards\": {");
		for (int i = 0; i < shards.size(); i++)
		{
			manifest.append(i == 0 ? "\n" : ",\n").append("    \"").append(shards.get(i)).append("\": \"").append(hashes.get(shards.get(i))).append('"');
		}
		manifest.append("\n  },\n  \"bands\": [\n");
		for (int index = 0; index < BANDS.length; index++)
		{
			final int lower = BANDS[index][0];
			final int upper = BANDS[index][1];
			int validated = 0;
			int reached = 0;
			int blocked = 0;
			for (Row candidate : generatedByKey.values())
			{
				if ((Integer.parseInt(candidate.get("npc_level_min")) > upper) || (Integer.parseInt(candidate.get("npc_level_max")) < lower))
				{
					continue;
				}
				final Row proof = anchorsByKey.get(candidate.get("coverage_key"));
				if (proof.get("status").equals("VALID"))
				{
					validated++;
					if (reachable.contains(proof.get("anchor_id")))
					{
						reached++;
					}
				}
				else
				{
					blocked++;
				}
			}
			manifest.append("    {\"band\": \"").append(lower).append('-').append(upper).append("\", \"validated\": ").append(validated).append(", \"reachable\": ").append(reached).append(", \"unreachableValidated\": ").append(validated - reached).append(", \"anchorBlocked\": ").append(blocked).append(", \"routeBlocked\": ").append(validated - reached).append('}').append(index == (BANDS.length - 1) ? "\n" : ",\n");
		}
		manifest.append("  ],\n  \"ruinsCandidateGroups\": ").append(ruins.get("candidate")).append(",\n  \"ruinsValidatedGroups\": ").append(ruins.get("validated")).append(",\n  \"ruinsLandmarkPathProven\": ").append(ruins.get("landmark_path")).append(",\n  \"ruinsPlannerReachableGroups\": ").append(ruins.get("reachable")).append("\n}\n");
		Files.writeString(output.resolve("VALIDATED_TOPOLOGY_MANIFEST.json"), manifest.toString(), StandardCharsets.UTF_8);
		System.out.println("GEO_PUBLICATION nodes=" + generatedNodes + " edges=" + publishedEdges + " shards=" + shards.size() + " reachable_anchors=" + reachable.size() + " ruins_reachable=" + ruins.get("reachable"));
	}

	private static Map<String, Row> unique(List<Row> rows, String column)
	{
		final Map<String, Row> result = new HashMap<>();
		for (Row row : rows)
		{
			if (result.putIfAbsent(row.get(column), row) != null)
			{
				throw new IllegalStateException("Duplicate proof key: " + row.get(column));
			}
		}
		return result;
	}

	private static String nodeXml(Row candidate)
	{
		final String[] territory = candidate.get("source_geometry").split(";", 2)[0].split(":", 4);
		if ((territory.length != 4) || !territory[0].equals("territory"))
		{
			throw new IllegalStateException("Accepted node lacks source polygon.");
		}
		final StringBuilder xml = new StringBuilder("\t<node id=\"").append(candidate.get("node_id")).append("\" kind=\"FARMING_AREA\" instanceId=\"0\" form=\"POLYGON\" minZ=\"").append(territory[1]).append("\" maxZ=\"").append(territory[2]).append("\" tags=\"outdoor-farming\">\n");
		for (String point : territory[3].split("\\|"))
		{
			final String[] xy = point.split(",", -1);
			xml.append("\t\t<vertex x=\"").append(xy[0]).append("\" y=\"").append(xy[1]).append("\" />\n");
		}
		xml.append("\t\t<source path=\"").append(candidate.get("source_refs")).append("\" />\n\t</node>\n");
		return xml.toString();
	}

	private static String anchorXml(Row candidate, Row proof)
	{
		final StringBuilder xml = new StringBuilder("\t<anchor id=\"").append(candidate.get("anchor_id")).append("\" role=\"FARMING\" nodeId=\"").append(candidate.get("node_id")).append("\" x=\"").append(proof.get("x")).append("\" y=\"").append(proof.get("y")).append("\" z=\"").append(proof.get("z")).append("\" instanceId=\"0\" tolerance=\"0\"");
		if (!candidate.get("map_region_loc_id").isEmpty())
		{
			xml.append(" mapRegionLocId=\"").append(candidate.get("map_region_loc_id")).append('"');
		}
		xml.append(" tags=\"outdoor-farming\">\n\t\t<source path=\"").append(candidate.get("source_refs")).append("\" />\n\t</anchor>\n");
		return xml.toString();
	}

	private static String edgeXml(Row candidate, Row proof)
	{
		final long milliseconds = Math.max(1000L, Long.parseLong(proof.get("length")) * 10L);
		final StringBuilder xml = new StringBuilder("\t<edge id=\"").append(candidate.get("route_id")).append(proof.get("direction").equals("F") ? ".f" : ".r").append("\" fromNodeId=\"").append(proof.get("from_node_id")).append("\" toNodeId=\"").append(proof.get("to_node_id")).append("\" mode=\"BACKGROUND\" bidirectional=\"false\" baseCost=\"1\" baseTravelMillis=\"").append(milliseconds).append("\" backgroundEligible=\"true\" channels=\"COMBAT,TARGETABILITY\" fromAnchorId=\"").append(proof.get("from_anchor_id")).append("\" toAnchorId=\"").append(proof.get("to_anchor_id")).append("\">\n");
		for (String source : new LinkedHashSet<>(Arrays.asList(candidate.get("source_refs").split("\\|"))))
		{
			xml.append("\t\t<source path=\"").append(source).append("\" />\n");
		}
		return xml.append("\t</edge>\n").toString();
	}

	private static List<String> writeShards(Path output, List<String> entities) throws Exception
	{
		return writeShards(output, entities, SHARD_BYTES);
	}

	static List<String> writeShards(Path output, List<String> entities, int shardBytes) throws Exception
	{
		final ArrayList<String> result = new ArrayList<>();
		final StringBuilder shard = new StringBuilder(ROOT);
		for (String entity : entities)
		{
			if ((ROOT + entity + END).getBytes(StandardCharsets.UTF_8).length > shardBytes)
			{
				throw new IllegalStateException("Generated entity exceeds shard bound.");
			}
			if (((shard.toString() + entity + END).getBytes(StandardCharsets.UTF_8).length > shardBytes) && (shard.length() > ROOT.length()))
			{
				result.add(writeShard(output, result.size(), shard));
				shard.setLength(0);
				shard.append(ROOT);
			}
			shard.append(entity);
		}
		if (shard.length() > ROOT.length())
		{
			result.add(writeShard(output, result.size(), shard));
		}
		if (result.isEmpty() || (result.size() >= 64))
		{
			throw new IllegalStateException("Invalid generated shard count.");
		}
		return result;
	}

	private static String writeShard(Path output, int number, StringBuilder xml) throws Exception
	{
		final String name = "high-five-generated-%02d.xml".formatted(number + 1);
		final byte[] bytes = (xml.toString() + END).getBytes(StandardCharsets.UTF_8);
		if (bytes.length >= (4 * 1024 * 1024))
		{
			throw new IllegalStateException("Generated shard exceeds loader's 4 MiB bound.");
		}
		Files.write(output.resolve(name), bytes);
		return name;
	}

	private static Map<String, String> coreAnchors(Path coreXml) throws Exception
	{
		final Element root = parse(coreXml);
		final Map<String, String> result = new HashMap<>();
		for (int i = 0; i < root.getChildNodes().getLength(); i++)
		{
			if ((root.getChildNodes().item(i) instanceof Element element) && element.getTagName().equals("anchor"))
			{
				result.put(element.getAttribute("id"), element.getAttribute("nodeId"));
			}
		}
		return result;
	}

	private static Map<String, List<String>> coreGraph(Path coreXml) throws Exception
	{
		final Element root = parse(coreXml);
		final Map<String, List<String>> graph = new HashMap<>();
		for (int i = 0; i < root.getChildNodes().getLength(); i++)
		{
			if ((root.getChildNodes().item(i) instanceof Element element) && element.getTagName().equals("edge") && element.getAttribute("mode").equals("BACKGROUND") && element.getAttribute("backgroundEligible").equals("true") && !element.getAttribute("fromAnchorId").isEmpty() && !element.getAttribute("toAnchorId").isEmpty())
			{
				graph.computeIfAbsent(element.getAttribute("fromAnchorId"), _ -> new ArrayList<>()).add(element.getAttribute("toAnchorId"));
			}
		}
		return graph;
	}

	private static Element parse(Path file) throws Exception
	{
		final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		return factory.newDocumentBuilder().parse(file.toFile()).getDocumentElement();
	}

	private static Set<String> reachable(Map<String, List<String>> graph, Set<String> coreAnchors)
	{
		final Set<String> reached = new HashSet<>();
		final ArrayDeque<String> queue = new ArrayDeque<>();
		for (String id : coreAnchors.stream().sorted().toList())
		{
			if (id.startsWith("population.ingress."))
			{
				reached.add(id);
				queue.add(id);
			}
		}
		while (!queue.isEmpty())
		{
			for (String next : graph.getOrDefault(queue.removeFirst(), List.of()))
			{
				if (reached.add(next))
				{
					queue.addLast(next);
				}
			}
		}
		return reached;
	}

	private static Map<String, Integer> ruins(Path validation, Map<String, Row> generated, Map<String, Row> proofs, Set<String> reachable) throws Exception
	{
		int candidate = 0;
		int validated = 0;
		int landmarkPath = 0;
		int reached = 0;
		final Set<String> keys = new HashSet<>();
		for (Row evidence : PhantomTopologyGeodataValidator.rows(validation.resolve("RUINS_GEODATA_VALIDATION.tsv")))
		{
			final String key = evidence.get("coverage_key");
			if (!keys.add(key) || !generated.containsKey(key))
			{
				throw new IllegalStateException("Invalid Ruins candidate proof key: " + key);
			}
			candidate++;
			final Row proof = proofs.get(key);
			if (evidence.get("path_status").startsWith("VALID_"))
			{
				landmarkPath++;
			}
			if (proof.get("status").equals("VALID"))
			{
				validated++;
				if (reachable.contains(proof.get("anchor_id")))
				{
					reached++;
				}
			}
		}
		if (candidate != 10)
		{
			throw new IllegalStateException("Ruins candidate accounting changed.");
		}
		return Map.of("candidate", candidate, "validated", validated, "landmark_path", landmarkPath, "reachable", reached);
	}

	private static String sha256(Path file) throws Exception
	{
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
	}
}
