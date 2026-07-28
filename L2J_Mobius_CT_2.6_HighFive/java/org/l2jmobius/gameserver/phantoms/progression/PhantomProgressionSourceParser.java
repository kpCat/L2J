/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.progression;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.ActorKind;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.PetSkillFact;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.SkillRef;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.TargetScope;

/**
 * Strict bounded parser for facts that current public loaders do not expose:
 * XML skill-tree parents/source identities, summon-effect parameters, complete
 * pet identities and curated capability semantics.
 */
public final class PhantomProgressionSourceParser
{
	private static final long MAXIMUM_XML_BYTES = 32L * 1024 * 1024;
	private final Path _datapackRoot;
	private final PhantomProgressionPolicy _policy;

	public PhantomProgressionSourceParser(Path datapackRoot, PhantomProgressionPolicy policy)
	{
		_datapackRoot = datapackRoot.toAbsolutePath().normalize();
		_policy = policy;
	}

	public SourceData parse()
	{
		try
		{
			final TreeData trees = parseSkillTrees();
			final SkillSourceData skills = parseSkillSources();
			final List<RawPet> pets = parsePets();
			final CapabilityData capabilities = parseCapabilities();
			return new SourceData(trees.skillTreeParents(), trees.skillSources(), skills.skillSources(), skills.summons(), pets, capabilities.semantics(), capabilities.rules());
		}
		catch (ProgressionSourceException exception)
		{
			throw exception;
		}
		catch (Exception exception)
		{
			throw new ProgressionSourceException("parse", "Unable to parse progression sources.", exception);
		}
	}

	private TreeData parseSkillTrees() throws Exception
	{
		final Path root = source("data/stats/players/skillTrees");
		final HashMap<Integer, Integer> parents = new HashMap<>();
		final HashMap<TreeSkillKey, String> sources = new HashMap<>();
		for (Path file : xmlFiles(root))
		{
			final String relative = relative(file);
			final Element documentRoot = parseDocument(file).getDocumentElement();
			for (Element tree : childElements(documentRoot))
			{
				if (!"skillTree".equals(tree.getTagName()))
				{
					throw failure("schema", "Unknown skill-tree root child.");
				}
				final String type = required(tree, "type");
				final int classId = tree.hasAttribute("classId") ? integer(tree, "classId") : -1;
				if ("classSkillTree".equals(type) && (classId < 0))
				{
					throw failure("schema", "Class skill tree lacks classId.");
				}
				if (tree.hasAttribute("parentClassId"))
				{
					final Integer previous = parents.putIfAbsent(classId, integer(tree, "parentClassId"));
					if (previous != null)
					{
						throw failure("duplicate", "Duplicate class skill-tree parent.");
					}
				}
				for (Element skill : childElements(tree))
				{
					if (!"skill".equals(skill.getTagName()))
					{
						continue;
					}
					final TreeSkillKey key = new TreeSkillKey(type, classId, integer(skill, "skillId"), integer(skill, "skillLevel"));
					// The stock loader is map-based and accepts repeated direct entries.
					// Preserve one deterministic source identity without inventing a
					// second learning fact.
					sources.putIfAbsent(key, relative);
				}
			}
		}
		return new TreeData(Map.copyOf(parents), Map.copyOf(sources));
	}

	private SkillSourceData parseSkillSources() throws Exception
	{
		final Path root = source("data/stats/skills");
		final HashMap<Integer, String> sources = new HashMap<>();
		final ArrayList<RawSummon> summons = new ArrayList<>();
		for (Path file : xmlFiles(root))
		{
			final String relative = relative(file);
			final Element documentRoot = parseDocument(file).getDocumentElement();
			for (Element skill : descendants(documentRoot, "skill"))
			{
				final int skillId = integer(skill, "id");
				final int levels = integer(skill, "levels");
				if ((skillId <= 0) || (levels <= 0))
				{
					throw failure("schema", "Invalid skill identity or level count.");
				}
				if (sources.putIfAbsent(skillId, relative) != null)
				{
					throw failure("duplicate", "Skill identity occurs in more than one source file.");
				}
				final Map<String, List<String>> tables = tables(skill);
				final Element effects = directChild(skill, "effects");
				if (effects == null)
				{
					continue;
				}
				for (Element effect : childElements(effects))
				{
					if (!"effect".equals(effect.getTagName()))
					{
						throw failure("schema", "Unknown effects child.");
					}
					final String effectName = required(effect, "name");
					final ActorKind kind;
					if ("Summon".equals(effectName))
					{
						kind = ActorKind.SERVITOR;
					}
					else if ("SummonCubic".equals(effectName))
					{
						kind = ActorKind.CUBIC;
					}
					else if ("SummonPet".equals(effectName))
					{
						continue; // Exact pet relations are reconstructed from control items below.
					}
					else
					{
						continue;
					}
					for (int level = 1; level <= levels; level++)
					{
						if (summons.size() >= _policy.maximumSummonFacts())
						{
							throw failure("count", "Summon effect count exceeds policy.");
						}
						final int actorIdentity = integerValue(effect, kind == ActorKind.CUBIC ? "cubicId" : "npcId", level, tables, kind == ActorKind.CUBIC ? -1 : 0);
						final int lifetimeMillis = Math.multiplyExact(integerValue(effect, kind == ActorKind.CUBIC ? "cubicDuration" : "lifeTime", level, tables, kind == ActorKind.CUBIC ? 0 : 3600), 1000);
						final double expMultiplier = kind == ActorKind.CUBIC ? 0 : doubleValue(effect, "expMultiplier", level, tables, 1);
						final int upkeepItemId = integerValue(effect, "consumeItemId", level, tables, 0);
						final int upkeepItemCount = upkeepItemId == 0 ? 0 : integerValue(effect, "consumeItemCount", level, tables, 1);
						final int upkeepInterval = Math.multiplyExact(integerValue(effect, "consumeItemInterval", level, tables, 0), 1000);
						summons.add(new RawSummon(skillId, level, actorIdentity, kind, lifetimeMillis, expMultiplier, upkeepItemId, upkeepItemCount, upkeepInterval, relative));
					}
				}
			}
		}
		summons.sort(Comparator.comparingInt(RawSummon::skillId).thenComparingInt(RawSummon::skillLevel).thenComparing(RawSummon::actorKind).thenComparingInt(RawSummon::actorIdentity));
		return new SkillSourceData(Map.copyOf(sources), List.copyOf(summons));
	}

	private List<RawPet> parsePets() throws Exception
	{
		final ArrayList<RawPet> result = new ArrayList<>();
		for (Path file : xmlFiles(source("data/stats/pets")))
		{
			final String relative = relative(file);
			for (Element pet : descendants(parseDocument(file).getDocumentElement(), "pet"))
			{
				if (result.size() >= _policy.maximumPetFacts())
				{
					throw failure("count", "Pet count exceeds policy.");
				}
				final int npcId = integer(pet, "id");
				final int itemId = integer(pet, "itemId");
				final HashSet<Integer> food = new HashSet<>();
				final ArrayList<PetSkillFact> skills = new ArrayList<>();
				int load = 20000;
				int hungryLimit = 1;
				boolean syncLevel = false;
				int minimumLevel = Integer.MAX_VALUE;
				int maximumLevel = 0;
				for (Element child : childElements(pet))
				{
					switch (child.getTagName())
					{
						case "set":
						{
							final String name = required(child, "name");
							final String value = required(child, "val");
							switch (name)
							{
								case "food":
									for (String token : value.split(";"))
									{
										food.add(parseInteger(token, "pet food"));
									}
									break;
								case "load":
									load = parseInteger(value, "pet load");
									break;
								case "hungry_limit":
									hungryLimit = parseInteger(value, "pet hunger");
									break;
								case "sync_level":
									syncLevel = parseInteger(value, "pet sync") == 1;
									break;
								default:
									break; // evolve is intentionally not a runtime PetData field.
							}
							break;
						}
						case "skills":
							for (Element skill : childElements(child))
							{
								skills.add(new PetSkillFact(integer(skill, "skillId"), integer(skill, "skillLevel"), integer(skill, "minLevel")));
							}
							break;
						case "stats":
							for (Element stat : childElements(child))
							{
								final int level = integer(stat, "level");
								minimumLevel = Math.min(minimumLevel, level);
								maximumLevel = Math.max(maximumLevel, level);
							}
							break;
						default:
							throw failure("schema", "Unknown pet element.");
					}
				}
				if (minimumLevel == Integer.MAX_VALUE)
				{
					throw failure("schema", "Pet has no level stats.");
				}
				skills.sort(Comparator.comparingInt(PetSkillFact::skillId).thenComparingInt(PetSkillFact::skillLevel).thenComparingInt(PetSkillFact::minimumPetLevel));
				result.add(new RawPet(npcId, itemId, Set.copyOf(food), minimumLevel, maximumLevel, load, hungryLimit, syncLevel, List.copyOf(skills), relative));
			}
		}
		result.sort(Comparator.comparingInt(RawPet::npcId));
		return List.copyOf(result);
	}

	private CapabilityData parseCapabilities() throws Exception
	{
		final HashMap<String, CapabilitySemantics> semantics = new HashMap<>();
		final ArrayList<CapabilitySeed> rules = new ArrayList<>();
		for (Path file : xmlFiles(source("data/phantoms/progression")))
		{
			final Element root = parseDocument(file).getDocumentElement();
			if ((root == null) || !"progression".equals(root.getTagName()) || (integer(root, "schemaVersion") != 1))
			{
				throw failure("schema", "Unexpected progression capability root.");
			}
			for (Element child : childElements(root))
			{
				switch (child.getTagName())
				{
					case "capabilitySemantics":
					{
						final String key = capabilityKey(child);
						final Set<String> families = child.hasAttribute("equipmentFamilies") ? Set.of(required(child, "equipmentFamilies").split(";")) : Set.of();
						final CapabilitySemantics value = new CapabilitySemantics(key, enumValue(TargetScope.class, required(child, "targetScope")), families);
						if (semantics.putIfAbsent(key, value) != null)
						{
							throw failure("duplicate", "Duplicate capability semantics.");
						}
						break;
					}
					case "capabilityRule":
						rules.add(new CapabilitySeed(integer(child, "classId"), capabilityKey(child), integer(child, "rank"), new SkillRef(integer(child, "skillId"), integer(child, "skillLevel")), required(child, "source")));
						break;
					default:
						throw failure("schema", "Unknown progression capability element.");
				}
			}
		}
		rules.sort(Comparator.comparingInt(CapabilitySeed::classId).thenComparing(CapabilitySeed::capabilityKey).thenComparingInt(CapabilitySeed::rank));
		return new CapabilityData(Map.copyOf(semantics), List.copyOf(rules));
	}

	private Path source(String relative)
	{
		final Path path = _datapackRoot.resolve(relative).normalize();
		if (!path.startsWith(_datapackRoot) || !Files.exists(path))
		{
			throw failure("io", "Required progression source is missing.");
		}
		return path;
	}

	private List<Path> xmlFiles(Path root) throws Exception
	{
		try (Stream<Path> stream = Files.walk(root))
		{
			final List<Path> result = stream.filter(Files::isRegularFile).filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".xml")).sorted().toList();
			if (result.isEmpty() || (result.size() > 1000))
			{
				throw failure("count", "Progression XML source file count is outside policy.");
			}
			for (Path file : result)
			{
				if (Files.size(file) > MAXIMUM_XML_BYTES)
				{
					throw failure("count", "Progression XML source exceeds byte policy.");
				}
			}
			return result;
		}
	}

	private Document parseDocument(Path path) throws Exception
	{
		final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
		factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
		factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
		factory.setXIncludeAware(false);
		factory.setExpandEntityReferences(false);
		factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
		factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
		try (InputStream stream = Files.newInputStream(path))
		{
			return factory.newDocumentBuilder().parse(stream);
		}
	}

	private String relative(Path path)
	{
		return _datapackRoot.relativize(path.toAbsolutePath().normalize()).toString().replace('\\', '/');
	}

	private static Map<String, List<String>> tables(Element skill)
	{
		final HashMap<String, List<String>> result = new HashMap<>();
		for (Element child : childElements(skill))
		{
			if ("table".equals(child.getTagName()))
			{
				final String name = required(child, "name");
				final List<String> values = List.of(child.getTextContent().trim().split("\\s+"));
				if (result.putIfAbsent(name, values) != null)
				{
					throw failure("duplicate", "Duplicate skill table.");
				}
			}
		}
		return Map.copyOf(result);
	}

	private static int integerValue(Element effect, String name, int level, Map<String, List<String>> tables, int defaultValue)
	{
		final String value = value(effect, name, level, tables);
		return value == null ? defaultValue : parseInteger(value, "summon parameter");
	}

	private static double doubleValue(Element effect, String name, int level, Map<String, List<String>> tables, double defaultValue)
	{
		final String value = value(effect, name, level, tables);
		if (value == null)
		{
			return defaultValue;
		}
		try
		{
			return Double.parseDouble(value);
		}
		catch (NumberFormatException exception)
		{
			throw failure("schema", "Invalid summon decimal.", exception);
		}
	}

	private static String value(Element effect, String name, int level, Map<String, List<String>> tables)
	{
		final Element child = directChild(effect, name);
		if (child == null)
		{
			return null;
		}
		final String raw = child.getTextContent().trim();
		if (!raw.startsWith("#"))
		{
			return raw;
		}
		final List<String> values = tables.get(raw);
		if ((values == null) || (level > values.size()))
		{
			throw failure("reference", "Summon parameter table does not cover the skill level.");
		}
		return values.get(level - 1);
	}

	private static Element directChild(Element parent, String name)
	{
		for (Element child : childElements(parent))
		{
			if (name.equals(child.getTagName()))
			{
				return child;
			}
		}
		return null;
	}

	private static List<Element> childElements(Element parent)
	{
		final ArrayList<Element> result = new ArrayList<>();
		final NodeList children = parent.getChildNodes();
		for (int index = 0; index < children.getLength(); index++)
		{
			final Node child = children.item(index);
			if (child.getNodeType() == Node.ELEMENT_NODE)
			{
				result.add((Element) child);
			}
			else if ((child.getNodeType() == Node.TEXT_NODE) && !child.getTextContent().isBlank())
			{
				throw failure("schema", "Unexpected XML text.");
			}
		}
		return result;
	}

	private static List<Element> descendants(Element parent, String name)
	{
		final ArrayList<Element> result = new ArrayList<>();
		if (name.equals(parent.getTagName()))
		{
			result.add(parent);
		}
		final NodeList children = parent.getChildNodes();
		for (int index = 0; index < children.getLength(); index++)
		{
			final Node child = children.item(index);
			if (child.getNodeType() == Node.ELEMENT_NODE)
			{
				result.addAll(descendants((Element) child, name));
			}
		}
		return result;
	}

	private static String required(Element element, String name)
	{
		if (!element.hasAttribute(name) || element.getAttribute(name).isBlank())
		{
			throw failure("schema", "Missing required XML attribute.");
		}
		return element.getAttribute(name);
	}

	private static String capabilityKey(Element element)
	{
		final String value = required(element, "capabilityKey");
		if (!value.matches("[a-z][a-z0-9]*(?:\\.[a-z][a-z0-9_]*)+"))
		{
			throw failure("schema", "Invalid capability key.");
		}
		return value;
	}

	private static int integer(Element element, String name)
	{
		return parseInteger(required(element, name), name);
	}

	private static int parseInteger(String value, String label)
	{
		try
		{
			return Integer.parseInt(value);
		}
		catch (NumberFormatException exception)
		{
			throw failure("schema", "Invalid " + label + " integer.", exception);
		}
	}

	private static <E extends Enum<E>> E enumValue(Class<E> type, String value)
	{
		try
		{
			return Enum.valueOf(type, value);
		}
		catch (IllegalArgumentException exception)
		{
			throw failure("schema", "Invalid progression enum.", exception);
		}
	}

	private static ProgressionSourceException failure(String category, String message)
	{
		return new ProgressionSourceException(category, message);
	}

	private static ProgressionSourceException failure(String category, String message, Throwable cause)
	{
		return new ProgressionSourceException(category, message, cause);
	}

	public record TreeSkillKey(String treeType, int classId, int skillId, int skillLevel)
	{
	}

	public record RawSummon(int skillId, int skillLevel, int actorIdentity, ActorKind actorKind, int lifetimeMillis, double expMultiplier, int upkeepItemId, int upkeepItemCount, int upkeepIntervalMillis, String sourcePath)
	{
	}

	public record RawPet(int npcId, int controlItemId, Set<Integer> foodItemIds, int minimumLevel, int maximumLevel, int load, int hungryLimit, boolean synchronizedLevel, List<PetSkillFact> skills, String sourcePath)
	{
	}

	public record CapabilitySemantics(String capabilityKey, TargetScope targetScope, Set<String> equipmentFamilies)
	{
		public CapabilitySemantics
		{
			equipmentFamilies = Set.copyOf(equipmentFamilies);
		}
	}

	public record CapabilitySeed(int classId, String capabilityKey, int rank, SkillRef skill, String sourcePath)
	{
	}

	public record SourceData(Map<Integer, Integer> skillTreeParents, Map<TreeSkillKey, String> treeSkillSources, Map<Integer, String> skillSources, List<RawSummon> summons, List<RawPet> pets, Map<String, CapabilitySemantics> capabilitySemantics, List<CapabilitySeed> capabilitySeeds)
	{
		public SourceData
		{
			skillTreeParents = Map.copyOf(skillTreeParents);
			treeSkillSources = Map.copyOf(treeSkillSources);
			skillSources = Map.copyOf(skillSources);
			summons = List.copyOf(summons);
			pets = List.copyOf(pets);
			capabilitySemantics = Map.copyOf(capabilitySemantics);
			capabilitySeeds = List.copyOf(capabilitySeeds);
		}
	}

	public static final class ProgressionSourceException extends IllegalStateException
	{
		private static final long serialVersionUID = 1L;
		private final String _category;

		ProgressionSourceException(String category, String message)
		{
			super(message);
			_category = category;
		}

		ProgressionSourceException(String category, String message, Throwable cause)
		{
			super(message, cause);
			_category = category;
		}

		public String category()
		{
			return _category;
		}
	}

	private record TreeData(Map<Integer, Integer> skillTreeParents, Map<TreeSkillKey, String> skillSources)
	{
	}

	private record SkillSourceData(Map<Integer, String> skillSources, List<RawSummon> summons)
	{
	}

	private record CapabilityData(Map<String, CapabilitySemantics> semantics, List<CapabilitySeed> rules)
	{
	}
}
