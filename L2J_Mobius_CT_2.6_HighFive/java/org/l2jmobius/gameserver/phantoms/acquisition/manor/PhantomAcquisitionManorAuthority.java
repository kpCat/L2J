/*
 * Copyright (c) 2013 L2jMobius
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 */
package org.l2jmobius.gameserver.phantoms.acquisition.manor;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import org.l2jmobius.gameserver.config.GeneralConfig;
import org.l2jmobius.gameserver.config.RatesConfig;
import org.l2jmobius.gameserver.data.xml.ItemData;
import org.l2jmobius.gameserver.data.xml.MapRegionData;
import org.l2jmobius.gameserver.data.xml.NpcData;
import org.l2jmobius.gameserver.handler.IItemHandler;
import org.l2jmobius.gameserver.handler.ItemHandler;
import org.l2jmobius.gameserver.managers.CastleManorManager;
import org.l2jmobius.gameserver.model.item.EtcItem;
import org.l2jmobius.gameserver.model.item.ItemTemplate;
import org.l2jmobius.gameserver.model.skill.holders.SkillHolder;
import org.l2jmobius.gameserver.model.siege.manor.Seed;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.ManorBinding;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.KnowledgePage;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.ManorFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.NpcKind;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.PageRequest;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.SpawnFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.SpawnPointKind;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.TargetFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.TargetQuery;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeQuery;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchor;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyQuery;

/** Read-only current-manor authority and exact shipped formula mirror. */
public final class PhantomAcquisitionManorAuthority
{
	public static final int HARVESTER_ITEM_ID = 5125;
	private static final int MAX_FACTS = 8;
	private static final int MAX_TARGETS = 64;
	private final PhantomGameKnowledgeQuery _knowledge;
	private final PhantomTopologyQuery _topology;
	private final Map<MapCell, Integer> _castleByMapCell;
	private final String _mapRegionHash;
	private final HandlerIdentity _harvester;
	private final String _authorityHash;

	public PhantomAcquisitionManorAuthority(PhantomGameKnowledgeQuery knowledge, PhantomTopologyQuery topology, Path mapRegionDirectory)
	{
		_knowledge = Objects.requireNonNull(knowledge, "knowledge");
		_topology = Objects.requireNonNull(topology, "topology");
		final RegionFacts regions = loadRegions(mapRegionDirectory);
		_castleByMapCell = regions.castleByMapCell();
		_mapRegionHash = regions.hash();
		_harvester = itemIdentity(HARVESTER_ITEM_ID, "Harvester");
		_authorityHash = calculateAuthorityHash();
	}

	public String authorityHash()
	{
		return _authorityHash;
	}

	public HandlerIdentity seedHandler(int seedItemId)
	{
		return registeredIdentity(seedItemId, "Seed");
	}

	public HandlerIdentity harvesterHandler()
	{
		return _harvester;
	}

	public boolean current()
	{
		try
		{
			requireRegistered(_harvester);
			for (ManorFact fact : _knowledge.snapshot().manorFacts())
			{
				requireRegistered(itemIdentity(fact.seedItemId(), "Seed"));
			}
			return _authorityHash.equals(calculateAuthorityHash());
		}
		catch (RuntimeException exception)
		{
			return false;
		}
	}

	public Probe probe(int cropItemId)
	{
		final List<ManorFact> facts = manorFacts(cropItemId);
		final LinkedHashSet<Integer> itemIds = new LinkedHashSet<>();
		for (ManorFact fact : facts)
		{
			itemIds.add(fact.seedItemId());
		}
		itemIds.add(HARVESTER_ITEM_ID);
		return new Probe(facts, itemIds.stream().sorted().toList());
	}

	public PlanningResult candidates(int cropItemId, int playerLevel, Map<Integer, Long> inventory)
	{
		if (!GeneralConfig.ALLOW_MANOR || CastleManorManager.getInstance().isUnderMaintenance() || !current())
		{
			return new PlanningResult(List.of(), "source.authority_stale");
		}
		if (inventory.getOrDefault(HARVESTER_ITEM_ID, 0L) < 1)
		{
			return new PlanningResult(List.of(), "manor.harvester_missing");
		}
		final ArrayList<Candidate> result = new ArrayList<>();
		boolean missingSeed = false;
		for (ManorFact fact : manorFacts(cropItemId))
		{
			if (inventory.getOrDefault(fact.seedItemId(), 0L) < 1)
			{
				missingSeed = true;
				continue;
			}
			final Seed runtime = CastleManorManager.getInstance().getSeed(fact.seedItemId());
			if (!matches(fact, runtime) || (ItemData.getInstance().getTemplate(fact.cropItemId()) == null))
			{
				continue;
			}
			final HandlerIdentity seedHandler = registeredIdentity(fact.seedItemId(), "Seed");
			final TargetQuery query = new TargetQuery(0, 100, Math.clamp(fact.seedLevel(), 0, 100), null, null, Set.of(NpcKind.MONSTER), true, true, true, null, null, PageRequest.first(MAX_TARGETS));
			final KnowledgePage<TargetFact> page = _knowledge.suitableTargets(query);
			for (TargetFact target : page.values())
			{
				final int sowChance = sowChance(fact.seedLevel(), fact.alternative(), target.npc().level(), playerLevel);
				if (sowChance <= 0)
				{
					continue;
				}
				final List<SpawnFact> spawns = _knowledge.snapshot().spawnFactsByNpc().getOrDefault(target.npc().npcId(), List.of()).stream().filter(spawn -> (spawn.instanceId() == 0) && (spawn.amount() > 0) && (spawn.pointKind() == SpawnPointKind.EXACT)).sorted(Comparator.comparing(SpawnFact::stableKey)).limit(64).toList();
				for (SpawnFact spawn : spawns)
				{
					final List<PhantomTopologyAnchor> anchors = _topology.snapshot().anchors().stream().filter(anchor -> (anchor.point().instanceId() == 0) && (castleForAnchor(anchor) == fact.castleId()) && withinActiveDistance(anchor, spawn)).sorted(Comparator.comparing(PhantomTopologyAnchor::id)).limit(4).toList();
					for (PhantomTopologyAnchor anchor : anchors)
					{
						final int multiplier = strongMultiplier(target.npc().npcId());
						final int payload = harvestPayload(target.npc().level(), fact.seedLevel(), multiplier, RatesConfig.RATE_DROP_MANOR);
						final String sourceId = digest("MANOR_V1", fact.stableKey(), target.npc().npcId(), spawn.stableKey(), anchor.id(), fact.castleId(), sowChance, harvestChance(playerLevel, target.npc().level()), payload, seedHandler.canonical(), _harvester.canonical(), _authorityHash, _knowledge.snapshot().combinedHash(), _topology.snapshot().canonicalHash());
						result.add(new Candidate(sourceId, fact, target.npc().npcId(), target.npc().level(), anchor.nodeId(), anchor.id(), sowChance, harvestChance(playerLevel, target.npc().level()), multiplier, payload, seedHandler));
					}
				}
			}
		}
		result.sort(Comparator.comparingInt(Candidate::sowChance).reversed().thenComparing(Comparator.comparingInt(Candidate::harvestChance).reversed()).thenComparing(Candidate::sourceId));
		final List<Candidate> bounded = result.stream().limit(64).toList();
		return new PlanningResult(bounded, bounded.isEmpty() && missingSeed ? "manor.seed_missing" : bounded.isEmpty() ? "source.target_unavailable" : "");
	}

	public ManorBinding binding(Candidate candidate, long seedCount, long cropCount)
	{
		final ManorFact fact = candidate.fact();
		return new ManorBinding(fact.castleId(), fact.seedItemId(), fact.cropItemId(), fact.matureItemId(), fact.reward1ItemId(), fact.reward2ItemId(), fact.seedLevel(), fact.alternative(), fact.rawSeedLimit(), fact.rawCropLimit(), 0, 0, seedCount, cropCount, _authorityHash);
	}

	public Projection projection(ManorBinding binding, int npcId, int playerLevel, int targetLevel)
	{
		if (!current() || !binding.authorityHash().equals(_authorityHash))
		{
			throw new IllegalStateException("Persisted manor authority is stale.");
		}
		final Seed seed = CastleManorManager.getInstance().getSeed(binding.seedItemId());
		final var template = NpcData.getInstance().getTemplate(npcId);
		if ((seed == null) || (seed.getCastleId() != binding.castleId()) || (seed.getCropId() != binding.cropItemId()) || (seed.getMatureId() != binding.matureItemId()) || (seed.getReward(1) != binding.reward1ItemId()) || (seed.getReward(2) != binding.reward2ItemId()) || (seed.getLevel() != binding.seedLevel()) || (seed.isAlternative() != binding.alternative()) || (seed.getSeedLimit() != (binding.rawSeedLimit() * RatesConfig.RATE_DROP_MANOR)) || (seed.getCropLimit() != (binding.rawCropLimit() * RatesConfig.RATE_DROP_MANOR)) || (template == null) || (template.getLevel() != targetLevel) || !template.canBeSown())
		{
			throw new IllegalStateException("Persisted manor formula input is stale.");
		}
		registeredIdentity(binding.seedItemId(), "Seed");
		final int multiplier = strongMultiplier(npcId);
		return new Projection(sowChance(binding.seedLevel(), binding.alternative(), targetLevel, playerLevel), harvestChance(playerLevel, targetLevel), harvestPayload(targetLevel, binding.seedLevel(), multiplier, RatesConfig.RATE_DROP_MANOR), multiplier, RatesConfig.RATE_DROP_MANOR);
	}

	public static int sowChance(int seedLevel, boolean alternative, int targetLevel, int playerLevel)
	{
		int chance = alternative ? 20 : 90;
		final int minimum = seedLevel - 5;
		final int maximum = seedLevel + 5;
		if (targetLevel < minimum)
		{
			chance -= 5 * (minimum - targetLevel);
		}
		if (targetLevel > maximum)
		{
			chance -= 5 * (targetLevel - maximum);
		}
		final int difference = Math.abs(playerLevel - targetLevel);
		if (difference > 5)
		{
			chance -= 5 * (difference - 5);
		}
		// Shipped Sow calls Math.max(chance, 1) without assigning the result.
		return chance;
	}

	public static int harvestChance(int playerLevel, int targetLevel)
	{
		final int difference = Math.abs(playerLevel - targetLevel);
		return Math.max(1, difference > 5 ? 100 - ((difference - 5) * 5) : 100);
	}

	public static int harvestPayload(int targetLevel, int seedLevel, int strongMultiplier, int manorRate)
	{
		if ((strongMultiplier < 1) || (manorRate < 0))
		{
			throw new IllegalArgumentException("Invalid manor harvest payload inputs.");
		}
		int count = strongMultiplier;
		final int difference = targetLevel - seedLevel - 5;
		if (difference > 0)
		{
			count += difference;
		}
		return Math.multiplyExact(count, manorRate);
	}

	private int castleForAnchor(PhantomTopologyAnchor anchor)
	{
		final int runtimeLocId = MapRegionData.getInstance().getMapRegionLocId(anchor.point().x(), anchor.point().y());
		if ((runtimeLocId <= 0) || ((anchor.mapRegionLocId() != null) && (anchor.mapRegionLocId() != runtimeLocId)))
		{
			return 0;
		}
		final int mapX = MapRegionData.getInstance().getMapRegionX(anchor.point().x());
		final int mapY = MapRegionData.getInstance().getMapRegionY(anchor.point().y());
		return _castleByMapCell.getOrDefault(new MapCell(mapX, mapY), 0);
	}

	private int castleForPoint(int x, int y)
	{
		return _castleByMapCell.getOrDefault(new MapCell(MapRegionData.getInstance().getMapRegionX(x), MapRegionData.getInstance().getMapRegionY(y)), 0);
	}

	private static boolean withinActiveDistance(PhantomTopologyAnchor anchor, SpawnFact spawn)
	{
		final long dx = (long) anchor.point().x() - spawn.x();
		final long dy = (long) anchor.point().y() - spawn.y();
		return ((dx * dx) + (dy * dy)) <= (2000L * 2000L);
	}

	private List<ManorFact> manorFacts(int cropItemId)
	{
		return _knowledge.manorSources(cropItemId, PageRequest.first(MAX_FACTS)).values().stream().filter(fact -> fact.cropItemId() == cropItemId).sorted(Comparator.comparing(ManorFact::stableKey)).toList();
	}

	private String calculateAuthorityHash()
	{
		final List<String> seeds = _knowledge.snapshot().manorFacts().stream().sorted(Comparator.comparing(ManorFact::stableKey)).map(fact ->
		{
			final Seed current = CastleManorManager.getInstance().getSeed(fact.seedItemId());
			final String runtime = current == null ? "missing" : String.join(":", Integer.toString(current.getCastleId()), Integer.toString(current.getSeedId()), Integer.toString(current.getCropId()), Integer.toString(current.getMatureId()), Integer.toString(current.getReward(1)), Integer.toString(current.getReward(2)), Integer.toString(current.getLevel()), Boolean.toString(current.isAlternative()), Long.toString(current.getSeedLimit()), Long.toString(current.getCropLimit()));
			return fact.toString() + ':' + runtime;
		}).toList();
		return digest("MANOR_AUTHORITY_V1", GeneralConfig.ALLOW_MANOR, CastleManorManager.getInstance().getCurrentModeName(), RatesConfig.RATE_DROP_MANOR, _mapRegionHash, _harvester.canonical(), seeds, _knowledge.snapshot().combinedHash(), _topology.snapshot().canonicalHash());
	}

	private static boolean matches(ManorFact fact, Seed seed)
	{
		return (seed != null) && (seed.getCastleId() == fact.castleId()) && (seed.getSeedId() == fact.seedItemId()) && (seed.getCropId() == fact.cropItemId()) && (seed.getMatureId() == fact.matureItemId()) && (seed.getReward(1) == fact.reward1ItemId()) && (seed.getReward(2) == fact.reward2ItemId()) && (seed.getLevel() == fact.seedLevel()) && (seed.isAlternative() == fact.alternative()) && (seed.getSeedLimit() == (fact.rawSeedLimit() * RatesConfig.RATE_DROP_MANOR)) && (seed.getCropLimit() == (fact.rawCropLimit() * RatesConfig.RATE_DROP_MANOR));
	}

	private static HandlerIdentity registeredIdentity(int itemId, String expectedHandler)
	{
		final HandlerIdentity identity = itemIdentity(itemId, expectedHandler);
		requireRegistered(identity);
		return identity;
	}

	private static HandlerIdentity itemIdentity(int itemId, String expectedHandler)
	{
		final ItemTemplate template = ItemData.getInstance().getTemplate(itemId);
		if (!(template instanceof EtcItem item) || !expectedHandler.equals(item.getHandlerName()))
		{
			throw new IllegalStateException("Canonical manor item handler identity changed: " + itemId);
		}
		final SkillHolder[] skills = item.getSkills();
		if ((skills == null) || (skills.length != 1))
		{
			throw new IllegalStateException("Canonical manor item skill identity changed: " + itemId);
		}
		return new HandlerIdentity(itemId, expectedHandler, skills[0].getSkillId(), skills[0].getSkillLevel());
	}

	private static void requireRegistered(HandlerIdentity identity)
	{
		final ItemTemplate template = ItemData.getInstance().getTemplate(identity.itemId());
		final IItemHandler handler = template instanceof EtcItem item ? ItemHandler.getInstance().getHandler(item) : null;
		if ((handler == null) || !identity.handlerName().equals(handler.getClass().getSimpleName()))
		{
			throw new IllegalStateException("Canonical manor item handler registration changed: " + identity.itemId());
		}
	}

	private static int strongMultiplier(int npcId)
	{
		final var template = NpcData.getInstance().getTemplate(npcId);
		if (template == null)
		{
			throw new IllegalArgumentException("Manor target template is absent.");
		}
		int result = 1;
		for (int skillId : template.getSkills().keySet().stream().sorted().toList())
		{
			if ((skillId >= 4303) && (skillId <= 4310))
			{
				result = Math.multiplyExact(result, skillId - 4301);
			}
		}
		return result;
	}

	private static RegionFacts loadRegions(Path directory)
	{
		try
		{
			final List<Path> files;
			try (var paths = Files.list(directory))
			{
				files = paths.filter(path -> path.getFileName().toString().endsWith(".xml")).sorted().toList();
			}
			if (files.isEmpty())
			{
				throw new IllegalArgumentException("Map-region authority is absent.");
			}
			final Map<String, RegionEntry> regions = new HashMap<>();
			final MessageDigest digest = MessageDigest.getInstance("SHA-256");
			for (Path file : files)
			{
				final byte[] bytes = Files.readAllBytes(file);
				digest.update(file.getFileName().toString().getBytes(StandardCharsets.UTF_8));
				digest.update((byte) 0);
				digest.update(bytes);
				final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
				factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
				factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
				factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
				factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
				factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
				factory.setXIncludeAware(false);
				factory.setExpandEntityReferences(false);
				final Element root = factory.newDocumentBuilder().parse(file.toFile()).getDocumentElement();
				for (Node node = root.getFirstChild(); node != null; node = node.getNextSibling())
				{
					if (node instanceof Element region && "region".equals(region.getTagName()))
					{
						final int castle = Integer.parseInt(region.getAttribute("castle"));
						final List<MapCell> cells = new ArrayList<>();
						for (Node child = region.getFirstChild(); child != null; child = child.getNextSibling())
						{
							if (child instanceof Element map && "map".equals(map.getTagName()))
							{
								cells.add(new MapCell(Integer.parseInt(map.getAttribute("X")), Integer.parseInt(map.getAttribute("Y"))));
							}
						}
						regions.put(region.getAttribute("name"), new RegionEntry(castle, cells));
					}
				}
			}
			final LinkedHashMap<MapCell, Integer> castles = new LinkedHashMap<>();
			for (RegionEntry region : regions.values())
			{
				for (MapCell cell : region.cells())
				{
					castles.put(cell, region.castleId());
				}
			}
			return new RegionFacts(Map.copyOf(castles), HexFormat.of().formatHex(digest.digest()));
		}
		catch (RuntimeException exception)
		{
			throw exception;
		}
		catch (Exception exception)
		{
			throw new IllegalArgumentException("Could not load map-region manor authority.", exception);
		}
	}

	private static String digest(Object... values)
	{
		try
		{
			final MessageDigest digest = MessageDigest.getInstance("SHA-256");
			for (Object value : values)
			{
				digest.update(Objects.toString(value).getBytes(StandardCharsets.UTF_8));
				digest.update((byte) 0);
			}
			return HexFormat.of().formatHex(digest.digest());
		}
		catch (Exception exception)
		{
			throw new IllegalStateException("SHA-256 is unavailable.", exception);
		}
	}

	public record Probe(List<ManorFact> facts, List<Integer> requiredItemIds)
	{
		public Probe
		{
			facts = List.copyOf(facts);
			requiredItemIds = List.copyOf(requiredItemIds);
		}
	}

	public record PlanningResult(List<Candidate> candidates, String reasonKey)
	{
		public PlanningResult
		{
			candidates = List.copyOf(candidates);
			reasonKey = Objects.requireNonNullElse(reasonKey, "");
		}
	}

	public record Candidate(String sourceId, ManorFact fact, int npcId, int npcLevel, String topologyNodeId, String anchorId, int sowChance, int harvestChance, int strongMultiplier, int harvestPayload, HandlerIdentity seedHandler)
	{
		public Candidate
		{
			if (!sourceId.matches("[0-9a-f]{64}") || (fact == null) || (npcId <= 0) || (npcLevel < 0) || (topologyNodeId == null) || topologyNodeId.isBlank() || (anchorId == null) || anchorId.isBlank() || (sowChance <= 0) || (harvestChance < 1) || (strongMultiplier < 1) || (harvestPayload < 0) || (seedHandler == null))
			{
				throw new IllegalArgumentException("Invalid manor acquisition candidate.");
			}
		}
	}

	public record HandlerIdentity(int itemId, String handlerName, int skillId, int skillLevel)
	{
		public HandlerIdentity
		{
			if ((itemId <= 0) || (handlerName == null) || handlerName.isBlank() || (skillId <= 0) || (skillLevel <= 0))
			{
				throw new IllegalArgumentException("Invalid manor handler identity.");
			}
		}

		private String canonical()
		{
			return itemId + ":" + handlerName + ":" + skillId + ":" + skillLevel;
		}
	}

	public record Projection(int sowChance, int harvestChance, int harvestPayload, int strongMultiplier, int manorRate)
	{
		public Projection
		{
			if ((sowChance <= 0) || (harvestChance < 1) || (harvestPayload < 0) || (strongMultiplier < 1) || (manorRate < 0))
			{
				throw new IllegalArgumentException("Invalid current manor projection.");
			}
		}
	}

	private record RegionFacts(Map<MapCell, Integer> castleByMapCell, String hash)
	{
	}

	private record MapCell(int x, int y)
	{
	}

	private record RegionEntry(int castleId, List<MapCell> cells)
	{
		private RegionEntry
		{
			cells = List.copyOf(cells);
		}
	}
}
