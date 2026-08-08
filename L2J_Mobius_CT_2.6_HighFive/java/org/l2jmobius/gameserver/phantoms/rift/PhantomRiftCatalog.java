/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.rift;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeMap;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/**
 * Strict immutable projection of canonical High Five Rift data and runtime
 * entry authority. Unsupported facts remain explicit and prevent readiness.
 */
public final class PhantomRiftCatalog
{
	private static final int MAX_BYTES = 256 * 1024;
	private static final Map<Integer, String> TYPE_KEYS = Map.of(1, "recruits", 2, "privates", 3, "officers", 4, "captains", 5, "commanders", 6, "heroes");
	private final Map<Integer, TierFact> _tiers;
	private final ConfigFacts _config;
	private final String _sourceHash;
	private final String _authorityHash;
	private final String _catalogHash;

	private PhantomRiftCatalog(Map<Integer, TierFact> tiers, ConfigFacts config, String sourceHash, String authorityHash, String catalogHash)
	{
		_tiers = Map.copyOf(tiers);
		_config = config;
		_sourceHash = sourceHash;
		_authorityHash = authorityHash;
		_catalogHash = catalogHash;
	}

	public TierFact requireTier(int type)
	{
		final TierFact tier = _tiers.get(type);
		if (tier == null)
		{
			throw new IllegalArgumentException("Unknown factual Rift type: " + type);
		}
		return tier;
	}

	public List<TierFact> tiers()
	{
		return _tiers.values().stream().sorted(Comparator.comparingInt(TierFact::type)).toList();
	}

	public ConfigFacts config()
	{
		return _config;
	}

	public String sourceHash()
	{
		return _sourceHash;
	}

	public String authorityHash()
	{
		return _authorityHash;
	}

	public String catalogHash()
	{
		return _catalogHash;
	}

	public static PhantomRiftCatalog load(Path source, Authority authority)
	{
		Objects.requireNonNull(source);
		Objects.requireNonNull(authority);
		try
		{
			final byte[] bytes = Files.readAllBytes(source);
			if ((bytes.length == 0) || (bytes.length > MAX_BYTES))
			{
				throw new IllegalArgumentException("DimensionalRift.xml is outside the strict byte bound.");
			}
			final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
			factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
			factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
			factory.setXIncludeAware(false);
			factory.setExpandEntityReferences(false);
			factory.setIgnoringComments(true);
			final Element root = factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes)).getDocumentElement();
			requireElement(root, "rift", Set.of("xmlns:xsi", "xsi:noNamespaceSchemaLocation"));
			final Map<Integer, TierFact> tiers = new TreeMap<>();
			for (Element area : childElements(root))
			{
				requireElement(area, "area", Set.of("type"));
				final int type = strictInt(area.getAttribute("type"), 1, 6, "Rift type");
				final EntryFacts entry = Objects.requireNonNull(authority.entry(type), "Rift entry authority returned null.");
				if (entry.type() != type)
				{
					throw new IllegalArgumentException("Rift entry authority type mismatch.");
				}
				final Map<Integer, RoomFact> rooms = new TreeMap<>();
				int tierMinimumLevel = Integer.MAX_VALUE;
				int tierMaximumLevel = Integer.MIN_VALUE;
				boolean levelsSupported = true;
				for (Element roomElement : childElements(area))
				{
					requireElement(roomElement, "room", Set.of("id"));
					final int roomId = strictInt(roomElement.getAttribute("id"), 1, 9, "Rift room");
					final List<SpawnFact> spawns = new ArrayList<>();
					final Set<Integer> roomMobIds = new java.util.HashSet<>();
					for (Element spawnElement : childElements(roomElement))
					{
						requireElement(spawnElement, "spawn", Set.of("mobId", "count", "delay"));
						final int mobId = strictInt(spawnElement.getAttribute("mobId"), 1, Integer.MAX_VALUE, "Rift mob ID");
						final int count = strictInt(spawnElement.getAttribute("count"), 1, 1000, "Rift spawn count");
						final int delay = strictInt(spawnElement.getAttribute("delay"), 1, 86400, "Rift spawn delay");
						if (!roomMobIds.add(mobId))
						{
							throw new IllegalArgumentException("Duplicate Rift mob in one room.");
						}
						final OptionalInt level = authority.npcLevel(mobId);
						final int factualLevel = level.orElse(-1);
						if (factualLevel < 1)
						{
							levelsSupported = false;
						}
						else
						{
							tierMinimumLevel = Math.min(tierMinimumLevel, factualLevel);
							tierMaximumLevel = Math.max(tierMaximumLevel, factualLevel);
						}
						spawns.add(new SpawnFact(mobId, count, delay, factualLevel));
					}
					if ((spawns.isEmpty()) || (spawns.size() > 3))
					{
						throw new IllegalArgumentException("Rift room spawn count is outside XSD bounds.");
					}
					if (rooms.put(roomId, new RoomFact(roomId, spawns, entry.bossRoomIds().contains(roomId))) != null)
					{
						throw new IllegalArgumentException("Duplicate Rift room.");
					}
				}
				if (rooms.size() != 9)
				{
					throw new IllegalArgumentException("Current High Five Rift tier must contain all nine rooms.");
				}
				final TierFact fact = new TierFact(type, TYPE_KEYS.get(type), new ArrayList<>(rooms.values()), levelsSupported ? tierMinimumLevel : -1, levelsSupported ? tierMaximumLevel : -1, entry, levelsSupported && entry.supported());
				if (tiers.put(type, fact) != null)
				{
					throw new IllegalArgumentException("Duplicate Rift type.");
				}
			}
			if (!tiers.keySet().equals(TYPE_KEYS.keySet()))
			{
				throw new IllegalArgumentException("Current High Five Rift catalog must contain factual types 1..6.");
			}
			final ConfigFacts config = Objects.requireNonNull(authority.config(), "Rift config authority returned null.");
			final String sourceHash = sha256(bytes);
			final StringBuilder canonical = new StringBuilder("RIFT_CATALOG_V1|").append(sourceHash).append('|').append(config.canonical());
			tiers.values().forEach(tier -> canonical.append('|').append(tier.canonical()));
			final String authorityHash = PhantomPartyModel.sha256(config.canonical() + tiers.values().stream().map(TierFact::authorityCanonical).reduce("", (left, right) -> left + '|' + right));
			return new PhantomRiftCatalog(tiers, config, sourceHash, authorityHash, PhantomPartyModel.sha256(canonical.toString()));
		}
		catch (RuntimeException e)
		{
			throw e;
		}
		catch (Exception e)
		{
			throw new IllegalArgumentException("Could not load strict factual Rift catalog.", e);
		}
	}

	private static List<Element> childElements(Element parent)
	{
		final List<Element> result = new ArrayList<>();
		for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling())
		{
			if (child instanceof Element element)
			{
				result.add(element);
			}
			else if ((child.getNodeType() == Node.TEXT_NODE) && !child.getTextContent().isBlank())
			{
				throw new IllegalArgumentException("Unexpected text in Rift XML.");
			}
		}
		return result;
	}

	private static void requireElement(Element element, String name, Set<String> allowedAttributes)
	{
		if (!name.equals(element.getTagName()))
		{
			throw new IllegalArgumentException("Unknown Rift element.");
		}
		final NamedNodeMap attributes = element.getAttributes();
		if (attributes.getLength() != allowedAttributes.size())
		{
			throw new IllegalArgumentException("Unexpected Rift attribute count.");
		}
		for (int index = 0; index < attributes.getLength(); index++)
		{
			final Node attribute = attributes.item(index);
			if (!allowedAttributes.contains(attribute.getNodeName()) || attribute.getNodeValue().isBlank())
			{
				throw new IllegalArgumentException("Unknown or blank Rift attribute.");
			}
		}
	}

	private static int strictInt(String value, int minimum, int maximum, String label)
	{
		if (!value.matches("[0-9]+"))
		{
			throw new IllegalArgumentException(label + " is not a strict integer.");
		}
		final long parsed = Long.parseLong(value);
		if ((parsed < minimum) || (parsed > maximum))
		{
			throw new IllegalArgumentException(label + " is outside bounds.");
		}
		return (int) parsed;
	}

	private static String sha256(byte[] bytes) throws Exception
	{
		return HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
	}

	public interface Authority
	{
		OptionalInt npcLevel(int npcId);

		EntryFacts entry(int type);

		ConfigFacts config();
	}

	public record EntryFacts(int type, boolean supported, int itemId, int itemCount, int minimumPartySize, int destinationX, int destinationY, int destinationZ, int destinationInstanceId, Set<Integer> bossRoomIds, int occupiedRooms, int capacity, boolean capacityAvailable, String provenance)
	{
		public EntryFacts
		{
			if ((type < 1) || (type > 6) || (itemId < 0) || (itemCount < 0) || (minimumPartySize < 0) || (destinationInstanceId < 0) || (occupiedRooms < 0) || (capacity < 0))
			{
				throw new IllegalArgumentException("Invalid Rift entry authority.");
			}
			bossRoomIds = Set.copyOf(bossRoomIds);
			if (bossRoomIds.stream().anyMatch(room -> (room < 1) || (room > 9)))
			{
				throw new IllegalArgumentException("Invalid factual Rift boss room.");
			}
			provenance = Objects.requireNonNull(provenance);
			if (supported && ((itemId <= 0) || (itemCount <= 0) || (minimumPartySize < 2) || (minimumPartySize > 9) || bossRoomIds.isEmpty() || (capacity < 1)))
			{
				throw new IllegalArgumentException("Supported Rift entry authority is incomplete.");
			}
		}

		private String canonical()
		{
			return type + ":" + supported + ":" + itemId + ":" + itemCount + ":" + minimumPartySize + ":" + destinationX + ":" + destinationY + ":" + destinationZ + ":" + destinationInstanceId + ":" + bossRoomIds.stream().sorted().toList() + ":" + occupiedRooms + ":" + capacity + ":" + capacityAvailable + ":" + provenance;
		}
	}

	public record ConfigFacts(int maximumJumps, int spawnDelayMillis, int autoJumpMinimumSeconds, int autoJumpMaximumSeconds, float bossRoomTimeMultiplier, Map<Integer, Integer> entryCosts, String provenance)
	{
		public ConfigFacts
		{
			if ((maximumJumps < 0) || (spawnDelayMillis < 0) || (autoJumpMinimumSeconds < 0) || (autoJumpMaximumSeconds < autoJumpMinimumSeconds) || !Float.isFinite(bossRoomTimeMultiplier) || (bossRoomTimeMultiplier <= 0))
			{
				throw new IllegalArgumentException("Invalid current Rift config facts.");
			}
			entryCosts = Map.copyOf(new TreeMap<>(entryCosts));
			if (!entryCosts.keySet().equals(TYPE_KEYS.keySet()) || entryCosts.values().stream().anyMatch(value -> value <= 0))
			{
				throw new IllegalArgumentException("Rift config must contain six positive current entry costs.");
			}
			provenance = Objects.requireNonNull(provenance);
		}

		public String hash()
		{
			return PhantomPartyModel.sha256(canonical());
		}

		private String canonical()
		{
			return maximumJumps + ":" + spawnDelayMillis + ":" + autoJumpMinimumSeconds + ":" + autoJumpMaximumSeconds + ":" + Float.toString(bossRoomTimeMultiplier) + ":" + entryCosts + ":" + provenance;
		}
	}

	public record SpawnFact(int mobId, int count, int delaySeconds, int npcLevel)
	{
		private String canonical()
		{
			return mobId + ":" + count + ":" + delaySeconds + ":" + npcLevel;
		}
	}

	public record RoomFact(int roomId, List<SpawnFact> spawns, boolean bossRoom)
	{
		public RoomFact
		{
			spawns = spawns.stream().sorted(Comparator.comparingInt(SpawnFact::mobId)).toList();
		}

		private String canonical()
		{
			return roomId + ":" + bossRoom + ":" + spawns.stream().map(SpawnFact::canonical).toList();
		}
	}

	public record TierFact(int type, String sourceKey, List<RoomFact> rooms, int minimumNpcLevel, int maximumNpcLevel, EntryFacts entry, boolean supported)
	{
		public TierFact
		{
			sourceKey = PhantomRiftModel.requireKey(sourceKey, "Rift source key");
			rooms = rooms.stream().sorted(Comparator.comparingInt(RoomFact::roomId)).toList();
			Objects.requireNonNull(entry);
			if (supported && ((minimumNpcLevel < 1) || (maximumNpcLevel < minimumNpcLevel)))
			{
				throw new IllegalArgumentException("Supported Rift tier lacks factual NPC levels.");
			}
		}

		public int totalSpawnCount()
		{
			return rooms.stream().flatMap(room -> room.spawns().stream()).mapToInt(SpawnFact::count).sum();
		}

		private String canonical()
		{
			return type + ":" + sourceKey + ":" + minimumNpcLevel + ":" + maximumNpcLevel + ":" + supported + ":" + rooms.stream().map(RoomFact::canonical).toList() + ":" + entry.canonical();
		}

		private String authorityCanonical()
		{
			return type + ":" + minimumNpcLevel + ":" + maximumNpcLevel + ":" + entry.canonical();
		}
	}
}
