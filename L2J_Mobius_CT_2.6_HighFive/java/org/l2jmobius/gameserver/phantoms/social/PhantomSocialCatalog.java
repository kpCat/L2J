/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.social;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel.AffiliationKind;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel.SocialState;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Strict XXE-safe, content-addressed tuning authority for social state.
 */
public final class PhantomSocialCatalog
{
	private static final int MAX_BYTES = 128 * 1024;
	private static final Set<String> REQUIRED_RELATIONSHIPS = Set.of("trust", "respect", "fear", "anger", "friendship", "rivalry", "debt");
	private static final Set<String> REQUIRED_REPUTATION = Set.of("reliability", "helpfulness", "competence", "hostility");
	private static final Set<String> REQUIRED_EVENTS = Set.of(
		"party.invite.accepted.outbound",
		"party.invite.accepted.inbound",
		"party.invite.refused.outbound",
		"party.invite.refused.inbound",
		"party.invite.expired.outbound",
		"party.member.joined",
		"party.member.left",
		"party.member.expelled",
		"party.leader.transferred",
		"party.support.received",
		"agreement.fulfilled",
		"agreement.broken",
		"debt.incurred",
		"debt.repaid");
	private static final Set<String> REQUIRED_MODIFIERS = Set.of(
		"goal.persistence",
		"risk.tolerance",
		"party.invite.preference",
		"party.support.priority",
		"conversation.warmth",
		"conflict.escalation");
	private static final List<String> AGREEMENT_KEYS = List.of("offered", "accepted", "fulfilled", "broken", "refused");

	public enum DimensionGroup
	{
		RELATIONSHIP,
		REPUTATION
	}

	public enum SourceGroup
	{
		TRAIT,
		DIMENSION,
		AGREEMENT
	}

	public enum EventSocialClass
	{
		SUPPORTIVE,
		ROUTINE_NEGATIVE,
		BETRAYAL,
		HOSTILE_COMBAT,
		NEUTRAL
	}

	public record TraitDefinition(int code, String key)
	{
		public TraitDefinition
		{
			requireCode(code);
			key = PhantomSocialModel.requireKey(key, "Trait key");
		}
	}

	public record DimensionDefinition(int code, String key, DimensionGroup group, int decayPerDay, int index)
	{
		public DimensionDefinition
		{
			requireCode(code);
			key = PhantomSocialModel.requireKey(key, "Dimension key");
			Objects.requireNonNull(group);
			if ((decayPerDay < 0) || (decayPerDay > PhantomSocialModel.MAX_VALUE) || (index < 0) || (index >= PhantomSocialModel.DIMENSION_COUNT))
			{
				throw new IllegalArgumentException("Social dimension metadata is outside bounds.");
			}
		}

		public String sourceKey()
		{
			return group == DimensionGroup.RELATIONSHIP ? "relationship." + key : "reputation." + key;
		}
	}

	public record EventDefinition(int code, String key, int ttlMinutes, int salience, EventSocialClass socialClass, int reputationShockBp, Map<Integer, Integer> dimensionDeltas, List<Integer> agreementDeltas)
	{
		public EventDefinition
		{
			requireCode(code);
			key = PhantomSocialModel.requireKey(key, "Social event key");
			if ((ttlMinutes < 1) || (ttlMinutes > 5_256_000) || (salience < 0) || (salience > PhantomSocialModel.MAX_VALUE) || (reputationShockBp < 0) || (reputationShockBp > 10000))
			{
				throw new IllegalArgumentException("Social event TTL, salience or reputation shock is outside bounds.");
			}
			Objects.requireNonNull(socialClass, "Social event class must not be null.");
			dimensionDeltas = Collections.unmodifiableMap(new TreeMap<>(dimensionDeltas));
			if ((agreementDeltas == null) || (agreementDeltas.size() != PhantomSocialModel.AGREEMENT_COUNT))
			{
				throw new IllegalArgumentException("Social event agreement vector is invalid.");
			}
			agreementDeltas = List.copyOf(agreementDeltas);
		}
	}

	public record ModifierWeight(String sourceKey, SourceGroup sourceGroup, int sourceIndex, int weight)
	{
		public ModifierWeight
		{
			sourceKey = PhantomSocialModel.requireKey(sourceKey, "Modifier source key");
			Objects.requireNonNull(sourceGroup);
			if ((sourceIndex < 0) || (weight < -3000) || (weight > 3000) || (weight == 0))
			{
				throw new IllegalArgumentException("Modifier source metadata is invalid.");
			}
		}
	}

	public record ModifierDefinition(String key, int minimum, int maximum, List<ModifierWeight> weights)
	{
		public ModifierDefinition
		{
			key = PhantomSocialModel.requireKey(key, "Modifier key");
			if ((minimum < -3000) || (maximum > 3000) || (minimum > 0) || (maximum < 0) || (minimum > maximum) || (weights == null) || weights.isEmpty() || (weights.size() > 32))
			{
				throw new IllegalArgumentException("Modifier clamp or weight count is invalid.");
			}
			weights = List.copyOf(weights);
		}
	}

	public record Limits(int relationships, int memories, int memorySalienceThreshold, int memoryDecayPerDay)
	{
		public Limits
		{
			if ((relationships < 1) || (relationships > PhantomSocialModel.MAX_RELATIONSHIPS) || (memories < 1) || (memories > PhantomSocialModel.MAX_MEMORIES) || (memorySalienceThreshold < 0) || (memorySalienceThreshold > PhantomSocialModel.MAX_VALUE) || (memoryDecayPerDay < 0) || (memoryDecayPerDay > PhantomSocialModel.MAX_VALUE))
			{
				throw new IllegalArgumentException("Social catalog limits are outside bounds.");
			}
		}
	}

	private final Map<String, TraitDefinition> _traitsByKey;
	private final Map<Integer, TraitDefinition> _traitsByCode;
	private final Map<String, DimensionDefinition> _dimensionsBySource;
	private final Map<Integer, DimensionDefinition> _dimensionsByCode;
	private final Map<String, EventDefinition> _eventsByKey;
	private final Map<Integer, EventDefinition> _eventsByCode;
	private final Map<String, ModifierDefinition> _modifiers;
	private final Limits _limits;
	private final String _hash;

	private PhantomSocialCatalog(List<TraitDefinition> traits, List<DimensionDefinition> dimensions, List<EventDefinition> events, List<ModifierDefinition> modifiers, Limits limits, String hash)
	{
		_traitsByKey = indexByKey(traits, TraitDefinition::key, "trait");
		_traitsByCode = indexByCode(traits, TraitDefinition::code, "trait");
		_dimensionsBySource = indexByKey(dimensions, DimensionDefinition::sourceKey, "dimension");
		_dimensionsByCode = indexByCode(dimensions, DimensionDefinition::code, "dimension");
		_eventsByKey = indexByKey(events, EventDefinition::key, "event");
		_eventsByCode = indexByCode(events, EventDefinition::code, "event");
		_modifiers = indexByKey(modifiers, ModifierDefinition::key, "modifier");
		_limits = Objects.requireNonNull(limits);
		_hash = PhantomSocialModel.requireHash(hash, "Social catalog hash");
	}

	public static PhantomSocialCatalog load(Path path)
	{
		try
		{
			final byte[] bytes = Files.readAllBytes(path);
			if ((bytes.length == 0) || (bytes.length > MAX_BYTES))
			{
				throw new IllegalArgumentException("Social catalog size is outside bounds.");
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
			final Element root = factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes)).getDocumentElement();
			requireElement(root, "socialCatalog", List.of("version"));
			if (!"1".equals(root.getAttribute("version")))
			{
				throw new IllegalArgumentException("Unknown social catalog version.");
			}
			final List<Element> sections = childElements(root);
			final List<String> expectedSections = List.of("limits", "traits", "relationships", "reputation", "events", "modifiers");
			if (!sections.stream().map(Element::getTagName).toList().equals(expectedSections))
			{
				throw new IllegalArgumentException("Social catalog sections are missing, duplicated or out of order.");
			}

			final Element limitsElement = sections.get(0);
			requireElement(limitsElement, "limits", List.of("relationships", "memories", "memorySalienceThreshold", "memoryDecayPerDay", "eviction"));
			if (!"expired-lowest-salience-oldest-hash".equals(limitsElement.getAttribute("eviction")))
			{
				throw new IllegalArgumentException("Unknown social memory eviction policy.");
			}
			final Limits limits = new Limits(
				strictInt(limitsElement.getAttribute("relationships"), 1, PhantomSocialModel.MAX_RELATIONSHIPS),
				strictInt(limitsElement.getAttribute("memories"), 1, PhantomSocialModel.MAX_MEMORIES),
				strictInt(limitsElement.getAttribute("memorySalienceThreshold"), 0, PhantomSocialModel.MAX_VALUE),
				strictInt(limitsElement.getAttribute("memoryDecayPerDay"), 0, PhantomSocialModel.MAX_VALUE));
			requireNoChildren(limitsElement);

			final Set<Integer> globalCodes = new HashSet<>();
			final List<TraitDefinition> traits = parseTraits(sections.get(1), globalCodes);
			final List<DimensionDefinition> dimensions = new ArrayList<>();
			dimensions.addAll(parseDimensions(sections.get(2), DimensionGroup.RELATIONSHIP, 0, REQUIRED_RELATIONSHIPS, globalCodes));
			dimensions.addAll(parseDimensions(sections.get(3), DimensionGroup.REPUTATION, REQUIRED_RELATIONSHIPS.size(), REQUIRED_REPUTATION, globalCodes));
			final Map<String, DimensionDefinition> dimensionsBySource = indexByKey(dimensions, DimensionDefinition::sourceKey, "dimension");
			final List<EventDefinition> events = parseEvents(sections.get(4), dimensionsBySource, globalCodes);
			final Map<String, TraitDefinition> traitsByKey = indexByKey(traits, TraitDefinition::key, "trait");
			final List<ModifierDefinition> modifiers = parseModifiers(sections.get(5), traitsByKey, dimensionsBySource);
			final String hash = HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
			return new PhantomSocialCatalog(traits, dimensions, events, modifiers, limits, hash);
		}
		catch (RuntimeException e)
		{
			throw e;
		}
		catch (Exception e)
		{
			throw new IllegalArgumentException("Could not load strict social catalog.", e);
		}
	}

	public String hash()
	{
		return _hash;
	}

	public Limits limits()
	{
		return _limits;
	}

	public List<TraitDefinition> traits()
	{
		return _traitsByCode.values().stream().toList();
	}

	public List<DimensionDefinition> dimensions()
	{
		return _dimensionsByCode.values().stream().toList();
	}

	public EventDefinition requireEvent(String key)
	{
		final EventDefinition result = _eventsByKey.get(key);
		if (result == null)
		{
			throw new IllegalArgumentException("Unknown social event key: " + key);
		}
		return result;
	}

	public EventDefinition requireEvent(int code)
	{
		final EventDefinition result = _eventsByCode.get(code);
		if (result == null)
		{
			throw new IllegalArgumentException("Unknown social event code.");
		}
		return result;
	}

	public List<EventDefinition> events()
	{
		return _eventsByCode.values().stream().toList();
	}

	public int affiliationMultiplierBp(AffiliationKind affiliation, EventSocialClass socialClass)
	{
		Objects.requireNonNull(affiliation, "Social affiliation must not be null.");
		Objects.requireNonNull(socialClass, "Social event class must not be null.");
		return switch (affiliation)
		{
			case NONE -> 10000;
			case SAME_CLAN -> switch (socialClass)
			{
				case SUPPORTIVE -> 12000;
				case ROUTINE_NEGATIVE -> 7000;
				case BETRAYAL -> 13000;
				case HOSTILE_COMBAT -> 8500;
				case NEUTRAL -> 10000;
			};
			case SAME_ALLIANCE -> switch (socialClass)
			{
				case SUPPORTIVE -> 11000;
				case ROUTINE_NEGATIVE -> 8500;
				case BETRAYAL -> 11500;
				case HOSTILE_COMBAT -> 9250;
				case NEUTRAL -> 10000;
			};
			case CLAN_WAR -> socialClass == EventSocialClass.HOSTILE_COMBAT ? 7000 : 10000;
		};
	}

	public ModifierDefinition requireModifier(String key)
	{
		final ModifierDefinition result = _modifiers.get(key);
		if (result == null)
		{
			throw new IllegalArgumentException("Unknown social modifier key: " + key);
		}
		return result;
	}

	public DimensionDefinition requireDimensionByCode(int code)
	{
		final DimensionDefinition result = _dimensionsByCode.get(code);
		if (result == null)
		{
			throw new IllegalArgumentException("Unknown social dimension code.");
		}
		return result;
	}

	public void validateState(SocialState state)
	{
		if (!state.traits().keySet().equals(_traitsByCode.keySet()))
		{
			throw new IllegalArgumentException("Social state trait authority is incomplete.");
		}
		for (var memory : state.memories())
		{
			requireEvent(memory.eventCode());
		}
	}

	public int agreementIndex(String key)
	{
		return AGREEMENT_KEYS.indexOf(key);
	}

	public String agreementKey(int index)
	{
		if ((index < 0) || (index >= AGREEMENT_KEYS.size()))
		{
			throw new IllegalArgumentException("Agreement index is outside bounds.");
		}
		return AGREEMENT_KEYS.get(index);
	}

	private static List<TraitDefinition> parseTraits(Element parent, Set<Integer> globalCodes)
	{
		requireElement(parent, "traits", List.of());
		final List<TraitDefinition> result = new ArrayList<>();
		for (Element element : children(parent, "trait"))
		{
			requireElement(element, "trait", List.of("code", "key"));
			requireNoChildren(element);
			final TraitDefinition definition = new TraitDefinition(strictInt(element.getAttribute("code"), 1, 65535), element.getAttribute("key"));
			requireUniqueCode(globalCodes, definition.code());
			result.add(definition);
		}
		if (result.isEmpty() || (result.size() > PhantomSocialModel.MAX_TRAITS))
		{
			throw new IllegalArgumentException("Social catalog trait count is outside bounds.");
		}
		indexByKey(result, TraitDefinition::key, "trait");
		indexByCode(result, TraitDefinition::code, "trait");
		return result;
	}

	private static List<DimensionDefinition> parseDimensions(Element parent, DimensionGroup group, int offset, Set<String> required, Set<Integer> globalCodes)
	{
		final String section = group == DimensionGroup.RELATIONSHIP ? "relationships" : "reputation";
		requireElement(parent, section, List.of());
		final List<DimensionSeed> seeds = new ArrayList<>();
		for (Element element : children(parent, "dimension"))
		{
			requireElement(element, "dimension", List.of("code", "key", "decayPerDay"));
			requireNoChildren(element);
			final int code = strictInt(element.getAttribute("code"), 1, 65535);
			requireUniqueCode(globalCodes, code);
			seeds.add(new DimensionSeed(code, PhantomSocialModel.requireKey(element.getAttribute("key"), "Dimension key"), strictInt(element.getAttribute("decayPerDay"), 0, PhantomSocialModel.MAX_VALUE)));
		}
		seeds.sort(java.util.Comparator.comparingInt(DimensionSeed::code));
		if (!seeds.stream().map(DimensionSeed::key).collect(java.util.stream.Collectors.toSet()).equals(required))
		{
			throw new IllegalArgumentException("Required social dimensions are incomplete.");
		}
		final List<DimensionDefinition> result = new ArrayList<>();
		for (int index = 0; index < seeds.size(); index++)
		{
			final DimensionSeed seed = seeds.get(index);
			result.add(new DimensionDefinition(seed.code(), seed.key(), group, seed.decay(), offset + index));
		}
		return result;
	}

	private static List<EventDefinition> parseEvents(Element parent, Map<String, DimensionDefinition> dimensions, Set<Integer> globalCodes)
	{
		requireElement(parent, "events", List.of());
		final List<EventDefinition> result = new ArrayList<>();
		for (Element element : children(parent, "event"))
		{
			requireElement(element, "event", List.of("code", "key", "ttlMinutes", "salience", "socialClass", "reputationShockBp"));
			final int code = strictInt(element.getAttribute("code"), 1, 65535);
			requireUniqueCode(globalCodes, code);
			final Map<Integer, Integer> deltas = new HashMap<>();
			final List<Integer> agreements = new ArrayList<>(Collections.nCopies(PhantomSocialModel.AGREEMENT_COUNT, 0));
			final Set<Integer> agreementSources = new HashSet<>();
			boolean hasEffect = false;
			for (Element child : childElements(element))
			{
				switch (child.getTagName())
				{
					case "delta":
					{
						requireElement(child, "delta", List.of("source", "value"));
						requireNoChildren(child);
						final DimensionDefinition source = dimensions.get(child.getAttribute("source"));
						if (source == null)
						{
							throw new IllegalArgumentException("Unknown social event delta source.");
						}
						final int value = strictInt(child.getAttribute("value"), -10000, 10000);
						if ((value == 0) || (deltas.put(source.index(), value) != null))
						{
							throw new IllegalArgumentException("Zero or duplicate social event delta source.");
						}
						hasEffect = true;
						break;
					}
					case "agreement":
					{
						requireElement(child, "agreement", List.of("key", "value"));
						requireNoChildren(child);
						final int index = AGREEMENT_KEYS.indexOf(child.getAttribute("key"));
						final int value = strictInt(child.getAttribute("value"), -100, 100);
						if ((index < 0) || (value == 0) || !agreementSources.add(index))
						{
							throw new IllegalArgumentException("Unknown, zero or duplicate social agreement delta.");
						}
						agreements.set(index, value);
						hasEffect = true;
						break;
					}
					default:
						throw new IllegalArgumentException("Unknown social event element.");
				}
			}
			if (!hasEffect)
			{
				throw new IllegalArgumentException("Social event has no declared effect.");
			}
			final EventSocialClass socialClass;
			try
			{
				socialClass = EventSocialClass.valueOf(element.getAttribute("socialClass"));
			}
			catch (IllegalArgumentException e)
			{
				throw new IllegalArgumentException("Unknown social event class.", e);
			}
			result.add(new EventDefinition(code, element.getAttribute("key"), strictInt(element.getAttribute("ttlMinutes"), 1, 5_256_000), strictInt(element.getAttribute("salience"), 0, PhantomSocialModel.MAX_VALUE), socialClass, strictInt(element.getAttribute("reputationShockBp"), 0, 10000), deltas, agreements));
		}
		final Map<String, EventDefinition> byKey = indexByKey(result, EventDefinition::key, "event");
		indexByCode(result, EventDefinition::code, "event");
		if (!byKey.keySet().containsAll(REQUIRED_EVENTS) || (result.size() > 64))
		{
			throw new IllegalArgumentException("Required social events are incomplete or excessive.");
		}
		return result;
	}

	private static List<ModifierDefinition> parseModifiers(Element parent, Map<String, TraitDefinition> traits, Map<String, DimensionDefinition> dimensions)
	{
		requireElement(parent, "modifiers", List.of());
		final List<ModifierDefinition> result = new ArrayList<>();
		for (Element element : children(parent, "modifier"))
		{
			requireElement(element, "modifier", List.of("key", "minimum", "maximum"));
			final List<ModifierWeight> weights = new ArrayList<>();
			final Set<String> sources = new HashSet<>();
			for (Element child : children(element, "weight"))
			{
				requireElement(child, "weight", List.of("source", "value"));
				requireNoChildren(child);
				final String source = child.getAttribute("source");
				if (!sources.add(source))
				{
					throw new IllegalArgumentException("Duplicate social modifier source.");
				}
				final SourceResolution resolution = resolveSource(source, traits, dimensions);
				weights.add(new ModifierWeight(source, resolution.group(), resolution.index(), strictInt(child.getAttribute("value"), -3000, 3000)));
			}
			result.add(new ModifierDefinition(element.getAttribute("key"), strictInt(element.getAttribute("minimum"), -3000, 3000), strictInt(element.getAttribute("maximum"), -3000, 3000), weights));
		}
		final Map<String, ModifierDefinition> byKey = indexByKey(result, ModifierDefinition::key, "modifier");
		if (!byKey.keySet().containsAll(REQUIRED_MODIFIERS) || (result.size() > 32))
		{
			throw new IllegalArgumentException("Required social modifiers are incomplete or excessive.");
		}
		return result;
	}

	private static SourceResolution resolveSource(String source, Map<String, TraitDefinition> traits, Map<String, DimensionDefinition> dimensions)
	{
		if (source.startsWith("trait."))
		{
			final TraitDefinition trait = traits.get(source.substring("trait.".length()));
			if (trait != null)
			{
				return new SourceResolution(SourceGroup.TRAIT, trait.code());
			}
		}
		final DimensionDefinition dimension = dimensions.get(source);
		if (dimension != null)
		{
			return new SourceResolution(SourceGroup.DIMENSION, dimension.index());
		}
		if (source.startsWith("agreement."))
		{
			final int index = AGREEMENT_KEYS.indexOf(source.substring("agreement.".length()));
			if (index >= 0)
			{
				return new SourceResolution(SourceGroup.AGREEMENT, index);
			}
		}
		throw new IllegalArgumentException("Unknown social modifier source.");
	}

	private static List<Element> children(Element parent, String name)
	{
		final List<Element> result = childElements(parent);
		if (result.stream().anyMatch(element -> !name.equals(element.getTagName())))
		{
			throw new IllegalArgumentException("Unknown social catalog element.");
		}
		return result;
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
				throw new IllegalArgumentException("Unexpected text in social catalog.");
			}
		}
		return result;
	}

	private static void requireElement(Element element, String name, List<String> attributes)
	{
		if (!name.equals(element.getTagName()) || (element.getAttributes().getLength() != attributes.size()))
		{
			throw new IllegalArgumentException("Invalid social catalog element.");
		}
		for (String attribute : attributes)
		{
			if (!element.hasAttribute(attribute) || element.getAttribute(attribute).isBlank())
			{
				throw new IllegalArgumentException("Missing social catalog attribute.");
			}
		}
	}

	private static void requireNoChildren(Element element)
	{
		if (!childElements(element).isEmpty())
		{
			throw new IllegalArgumentException("Unexpected nested social catalog element.");
		}
	}

	private static int strictInt(String value, int minimum, int maximum)
	{
		if ((value == null) || !value.matches("-?[0-9]+"))
		{
			throw new IllegalArgumentException("Invalid social catalog integer.");
		}
		final int result = Integer.parseInt(value);
		if ((result < minimum) || (result > maximum))
		{
			throw new IllegalArgumentException("Social catalog integer is outside bounds.");
		}
		return result;
	}

	private static void requireCode(int code)
	{
		if ((code < 1) || (code > 65535))
		{
			throw new IllegalArgumentException("Social catalog code is outside bounds.");
		}
	}

	private static void requireUniqueCode(Set<Integer> codes, int code)
	{
		if (!codes.add(code))
		{
			throw new IllegalArgumentException("Duplicate global social catalog code.");
		}
	}

	private static <T> Map<String, T> indexByKey(List<T> values, java.util.function.Function<T, String> key, String label)
	{
		final Map<String, T> result = new TreeMap<>();
		for (T value : values)
		{
			if (result.put(key.apply(value), value) != null)
			{
				throw new IllegalArgumentException("Duplicate social " + label + " key.");
			}
		}
		return Collections.unmodifiableMap(result);
	}

	private static <T> Map<Integer, T> indexByCode(List<T> values, java.util.function.ToIntFunction<T> code, String label)
	{
		final Map<Integer, T> result = new TreeMap<>();
		for (T value : values)
		{
			if (result.put(code.applyAsInt(value), value) != null)
			{
				throw new IllegalArgumentException("Duplicate social " + label + " code.");
			}
		}
		return Collections.unmodifiableMap(result);
	}

	private record DimensionSeed(int code, String key, int decay)
	{
	}

	private record SourceResolution(SourceGroup group, int index)
	{
	}
}
