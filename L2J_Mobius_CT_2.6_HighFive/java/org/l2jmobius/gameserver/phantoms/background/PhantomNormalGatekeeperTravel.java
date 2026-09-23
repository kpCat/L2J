package org.l2jmobius.gameserver.phantoms.background;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Element;

import org.l2jmobius.gameserver.data.xml.TeleporterData;
import org.l2jmobius.gameserver.config.PlayerConfig;
import org.l2jmobius.gameserver.model.actor.enums.player.TeleportType;
import org.l2jmobius.gameserver.model.teleporter.TeleportHolder;
import org.l2jmobius.gameserver.model.teleporter.TeleportLocation;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchor;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyEdge;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyEdgeMode;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyQuery;

/** One immutable route query for topology BACKGROUND and D1-proven NORMAL GK legs. */
public final class PhantomNormalGatekeeperTravel
{
	private static final int MAX_BYTES = 256 * 1024;
	private static final int MAX_LEGS = 128;
	private static final int MAX_ROUTE_STEPS = 128;
	private static final int MAX_VISITED_ANCHORS = 4096;
	private static final int MAX_CACHED_SOURCES = 64;
	private static final String FACTS_SHA = "a8318075ef6ea3c3f266aa975ef1565fb8d6c93d2f074f076a059ed5d2ea2fe4";
	private static final String CONNECTORS_SHA = "fe0c0433e8975ff43f397470eca5caf0ae3b545e4d66d0ece2096d1ed97c1926";
	private static final String TRANSITIONS_SHA = "d378de9ddb395914c7e36480f149143f3cb9a2b84284ca625708d5c880f3e09d";

	private final PhantomTopologyQuery _topology;
	private final List<Leg> _legs;
	private final String _hash;
	private final Map<String, List<Step>> _adjacent;
	private final Map<String, Map<String, Step>> _routesBySource = new ConcurrentHashMap<>();

	private PhantomNormalGatekeeperTravel(PhantomTopologyQuery topology, List<Leg> legs, String hash)
	{
		_topology = Objects.requireNonNull(topology, "topology");
		_legs = List.copyOf(legs);
		_hash = hash;
		final Map<String, List<Step>> adjacent = new HashMap<>();
		for (PhantomTopologyEdge edge : topology.snapshot().edges())
		{
			if (!edge.backgroundEligible() || (edge.mode() != PhantomTopologyEdgeMode.BACKGROUND) || (edge.fromAnchorId() == null) || (edge.toAnchorId() == null))
			{
				continue;
			}
			adjacent.computeIfAbsent(edge.fromAnchorId(), ignored -> new ArrayList<>()).add(new Step(Type.TOPOLOGY_BACKGROUND, edge.id(), edge.fromAnchorId(), edge.toAnchorId(), edge.baseTravelMillis(), null));
			if (edge.bidirectional())
			{
				adjacent.computeIfAbsent(edge.toAnchorId(), ignored -> new ArrayList<>()).add(new Step(Type.TOPOLOGY_BACKGROUND, edge.id(), edge.toAnchorId(), edge.fromAnchorId(), edge.baseTravelMillis(), null));
			}
		}
		for (Leg leg : legs)
		{
			adjacent.computeIfAbsent(leg.fromAnchorId(), ignored -> new ArrayList<>()).add(new Step(Type.NORMAL_GATEKEEPER, leg.id(), leg.fromAnchorId(), leg.toAnchorId(), leg.travelMillis(), leg));
		}
		adjacent.replaceAll((ignored, values) -> values.stream().sorted(Comparator.comparing(Step::id).thenComparing(Step::toAnchorId)).toList());
		_adjacent = Map.copyOf(adjacent);
	}

	public static PhantomNormalGatekeeperTravel empty(PhantomTopologyQuery topology)
	{
		return new PhantomNormalGatekeeperTravel(topology, List.of(), "0".repeat(64));
	}

	public static PhantomNormalGatekeeperTravel load(Path path, PhantomTopologyQuery topology)
	{
		try
		{
			final byte[] bytes = Files.readAllBytes(path);
			if ((bytes.length == 0) || (bytes.length > MAX_BYTES))
			{
				throw new IllegalArgumentException("Invalid NORMAL GK catalog size.");
			}
			final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
			factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
			factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
			factory.setXIncludeAware(false);
			factory.setExpandEntityReferences(false);
			final Element root = factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes)).getDocumentElement();
			if (!"travel".equals(root.getTagName()) || !"LIVE-002-D2/1".equals(root.getAttribute("schema")) || !FACTS_SHA.equals(root.getAttribute("factsSha256")) || !CONNECTORS_SHA.equals(root.getAttribute("connectorsSha256")) || !TRANSITIONS_SHA.equals(root.getAttribute("transitionsSha256")))
			{
				throw new IllegalArgumentException("NORMAL GK catalog D1 provenance changed.");
			}
			final List<Leg> legs = new ArrayList<>();
			final Set<String> ids = new HashSet<>();
			for (var node = root.getFirstChild(); node != null; node = node.getNextSibling())
			{
				if (!(node instanceof Element element))
				{
					continue;
				}
				if (!"leg".equals(element.getTagName()) || (legs.size() >= MAX_LEGS))
				{
					throw new IllegalArgumentException("Unexpected NORMAL GK catalog element or count.");
				}
				final Leg leg = parse(element, topology);
				if (!ids.add(leg.id()) || topology.snapshot().edgeById().containsKey(leg.id()))
				{
					throw new IllegalArgumentException("Duplicate NORMAL GK leg ID.");
				}
				legs.add(leg);
			}
			if (!legs.equals(legs.stream().sorted(Comparator.comparing(Leg::id)).toList()))
			{
				throw new IllegalArgumentException("NORMAL GK legs are not canonically ordered.");
			}
			return new PhantomNormalGatekeeperTravel(topology, legs, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
		}
		catch (Exception exception)
		{
			throw new IllegalArgumentException("Invalid NORMAL GK runtime catalog: " + path, exception);
		}
	}

	private static Leg parse(Element value, PhantomTopologyQuery topology) throws Exception
	{
		final String id = attribute(value, "id");
		final String from = attribute(value, "fromAnchorId");
		final String to = attribute(value, "toAnchorId");
		final String sourceConnector = attribute(value, "sourceConnectorId");
		final String transition = attribute(value, "transitionId");
		final String destinationConnector = attribute(value, "destinationConnectorId");
		final String compoundIdentity = sourceConnector + "|" + transition + "|" + destinationConnector;
		final String refs = attribute(value, "sourceRefs");
		final int npcId = number(value, "teleporterNpcId");
		final String listName = attribute(value, "teleportListName");
		final int index = number(value, "destinationIndex");
		final int x = number(value, "destinationX");
		final int y = number(value, "destinationY");
		final int z = number(value, "destinationZ");
		final int sourceX = number(value, "sourceX");
		final int sourceY = number(value, "sourceY");
		final int sourceZ = number(value, "sourceZ");
		final int feeId = number(value, "feeId");
		final long feeCount = Long.parseLong(attribute(value, "feeCount"));
		final long travelMillis = Long.parseLong(attribute(value, "travelMillis"));
		final PhantomTopologyAnchor departure = topology.findAnchor(from).orElseThrow();
		final PhantomTopologyAnchor arrival = topology.findAnchor(to).orElseThrow();
		if (!id.matches("leg[.][0-9a-f]{24}") || !sourceConnector.matches("connector[.][0-9a-f]{24}") || !destinationConnector.matches("connector[.][0-9a-f]{24}") || !transition.matches("transition[.][0-9a-f]{24}") || !id.equals("leg." + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(compoundIdentity.getBytes(StandardCharsets.US_ASCII))).substring(0, 24)) || refs.length() > 512 || !refs.contains("data/teleporters/") || !refs.contains("data/spawns/") || (departure.point().instanceId() != 0) || (arrival.point().instanceId() != 0) || (npcId <= 0) || (index < 0) || (index > 1024) || ((feeId != 0) && (feeId != 57)) || (feeCount < 0) || (feeCount > 1_000_000_000L) || (travelMillis < 1000) || (travelMillis > 86_400_000))
		{
			throw new IllegalArgumentException("Unsupported NORMAL GK leg.");
		}
		final Leg leg = new Leg(id, from, to, sourceConnector, transition, destinationConnector, npcId, listName, index, x, y, z, sourceX, sourceY, sourceZ, feeId, feeCount, travelMillis, refs);
		if (!matchesNative(leg))
		{
			throw new IllegalArgumentException("NORMAL GK native destination changed.");
		}
		return leg;
	}

	public static boolean matchesNative(Leg leg)
	{
		final TeleportHolder holder = TeleporterData.getInstance().getHolder(leg.teleporterNpcId(), leg.teleportListName());
		if ((holder == null) || (holder.getType() != TeleportType.NORMAL) || (leg.destinationIndex() >= holder.getLocations().size()))
		{
			return false;
		}
		final TeleportLocation location = holder.getLocation(leg.destinationIndex());
		return (location.getX() == leg.destinationX()) && (location.getY() == leg.destinationY()) && (location.getZ() == leg.destinationZ()) && (location.getFeeId() == leg.feeId()) && (location.getFeeCount() == leg.feeCount()) && location.getCastleId().isEmpty();
	}

	private static String attribute(Element value, String name)
	{
		final String result = value.getAttribute(name);
		if (result.isBlank())
		{
			throw new IllegalArgumentException("Missing NORMAL GK attribute: " + name);
		}
		return result;
	}

	private static int number(Element value, String name)
	{
		return Integer.parseInt(attribute(value, name));
	}

	public Optional<List<Step>> route(String fromAnchorId, String toAnchorId)
	{
		if (toAnchorId.equals(fromAnchorId))
		{
			return Optional.of(List.of());
		}
		if (_topology.findAnchor(fromAnchorId).isEmpty() || _topology.findAnchor(toAnchorId).isEmpty())
		{
			return Optional.empty();
		}
		Map<String, Step> previous = _routesBySource.get(fromAnchorId);
		if (previous == null)
		{
			previous = search(fromAnchorId);
			if (_routesBySource.size() >= MAX_CACHED_SOURCES)
			{
				_routesBySource.clear();
			}
			_routesBySource.putIfAbsent(fromAnchorId, previous);
		}
		if (!previous.containsKey(toAnchorId))
		{
			return Optional.empty();
		}
		final List<Step> result = new ArrayList<>();
		String cursor = toAnchorId;
		while (!cursor.equals(fromAnchorId) && (result.size() < MAX_ROUTE_STEPS))
		{
			final Step current = previous.get(cursor);
			result.add(current);
			cursor = current.fromAnchorId();
		}
		if (!cursor.equals(fromAnchorId))
		{
			return Optional.empty();
		}
		java.util.Collections.reverse(result);
		return Optional.of(List.copyOf(result));
	}

	private Map<String, Step> search(String fromAnchorId)
	{
		final ArrayDeque<String> queue = new ArrayDeque<>();
		final Map<String, Step> previous = new HashMap<>();
		final Set<String> visited = new HashSet<>();
		queue.add(fromAnchorId);
		visited.add(fromAnchorId);
		while (!queue.isEmpty())
		{
			for (Step step : _adjacent.getOrDefault(queue.removeFirst(), List.of()))
			{
				if ((step.type() == Type.TOPOLOGY_BACKGROUND) && !_topology.isTraversable(step.id()))
				{
					continue;
				}
				if (visited.contains(step.toAnchorId()) || (visited.size() >= MAX_VISITED_ANCHORS))
				{
					continue;
				}
				visited.add(step.toAnchorId());
				previous.put(step.toAnchorId(), step);
				queue.addLast(step.toAnchorId());
			}
		}
		return Map.copyOf(previous);
	}

	public List<Leg> legs()
	{
		return _legs;
	}

	public PhantomTopologyQuery topology()
	{
		return _topology;
	}

	public PhantomNormalGatekeeperTravel forTopology(PhantomTopologyQuery topology)
	{
		return topology == _topology ? this : new PhantomNormalGatekeeperTravel(topology, _legs, _hash);
	}

	public static long fee(Leg leg, int classIndex, int level, long logicalEpochMinute)
	{
		if ((classIndex < 0) || (level < 1) || (logicalEpochMinute < 0))
		{
			throw new IllegalArgumentException("Invalid NORMAL GK fee context.");
		}
		if ((leg.feeId() == 0) || (leg.feeCount() == 0) || ((classIndex == 0) && (level <= PlayerConfig.MAX_FREE_TELEPORT_LEVEL)))
		{
			return 0;
		}
		final ZonedDateTime time = Instant.ofEpochSecond(Math.multiplyExact(logicalEpochMinute, 60)).atZone(ZoneId.systemDefault());
		final int day = time.getDayOfWeek().getValue();
		return ((day == 1) || (day == 2)) && (time.getHour() >= 20) ? leg.feeCount() / 2 : leg.feeCount();
	}

	public String hash()
	{
		return _hash;
	}

	public enum Type
	{
		TOPOLOGY_BACKGROUND,
		NORMAL_GATEKEEPER
	}

	public record Step(Type type, String id, String fromAnchorId, String toAnchorId, long travelMillis, Leg gatekeeper)
	{
	}

	public record Leg(String id, String fromAnchorId, String toAnchorId, String sourceConnectorId, String transitionId, String destinationConnectorId, int teleporterNpcId, String teleportListName, int destinationIndex, int destinationX, int destinationY, int destinationZ, int sourceX, int sourceY, int sourceZ, int feeId, long feeCount, long travelMillis, String sourceRefs)
	{
	}
}
