/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.population;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyState.Disposition;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyState.Pace;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyState.Personality;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyState.Preset;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialCatalog;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialCatalog.TraitDefinition;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/** Strict content-addressed authority for independent population-life assignments. */
public final class PhantomPopulationEcologyCatalog
{
	private static final int MAX_BYTES = 128 * 1024;
	private static final int WEIGHT_TOTAL = 100;
	private static final int[] CYCLE_MULTIPLIERS =
	{
		17, 23, 29, 31, 37, 43
	};

	private final Limits _limits;
	private final Map<Personality, PersonalityDefinition> _personalities;
	private final Map<Preset, PresetDefinition> _presets;
	private final Map<String, Integer> _socialTraitCodes;
	private final String _hash;

	private PhantomPopulationEcologyCatalog(Limits limits, Map<Personality, PersonalityDefinition> personalities, Map<Preset, PresetDefinition> presets, Map<String, Integer> socialTraitCodes, String hash)
	{
		_limits = limits;
		_personalities = Collections.unmodifiableMap(new EnumMap<>(personalities));
		_presets = Collections.unmodifiableMap(new EnumMap<>(presets));
		_socialTraitCodes = Collections.unmodifiableMap(new TreeMap<>(socialTraitCodes));
		_hash = hash;
	}

	public static PhantomPopulationEcologyCatalog load(Path path, PhantomPopulationCatalog population, PhantomSocialCatalog social)
	{
		Objects.requireNonNull(path, "Ecology catalog path must not be null.");
		Objects.requireNonNull(population, "Population catalog must not be null.");
		Objects.requireNonNull(social, "Social catalog must not be null.");
		try
		{
			final byte[] bytes = Files.readAllBytes(path);
			if ((bytes.length == 0) || (bytes.length > MAX_BYTES))
			{
				throw new IllegalArgumentException("Ecology catalog size is outside bounds.");
			}
			final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
			factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
			factory.setXIncludeAware(false);
			factory.setExpandEntityReferences(false);
			final Element root = factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes)).getDocumentElement();
			requireElement(root, "ecology");
			requireExactAttributes(root, Set.of("schemaVersion", "productiveBlockMinutes", "maxProfilesPerPulse", "maxIntervalsPerPulse", "maxCalendarDaysPerPulse"));
			if (!"1".equals(root.getAttribute("schemaVersion")))
			{
				throw new IllegalArgumentException("Unsupported ecology catalog schema version.");
			}
			final Limits limits = new Limits(
				integer(root, "productiveBlockMinutes", 5, 60),
				integer(root, "maxProfilesPerPulse", 1, 64),
				integer(root, "maxIntervalsPerPulse", 1, 64),
				integer(root, "maxCalendarDaysPerPulse", 1, 366));
			if ((1440 % limits.productiveBlockMinutes()) != 0)
			{
				throw new IllegalArgumentException("Ecology productive block must divide one day exactly.");
			}
			final List<Element> sections = childElements(root);
			if ((sections.size() != 2) || !"personalities".equals(sections.get(0).getTagName()) || !"presets".equals(sections.get(1).getTagName()))
			{
				throw new IllegalArgumentException("Ecology catalog sections must be personalities and presets in canonical order.");
			}
			final Map<String, Integer> traitCodes = new TreeMap<>();
			for (TraitDefinition trait : social.traits())
			{
				traitCodes.put(trait.key(), trait.code());
			}
			final Map<Personality, PersonalityDefinition> personalities = parsePersonalities(sections.get(0), traitCodes.keySet());
			final Map<Preset, PresetDefinition> presets = parsePresets(sections.get(1), personalities.keySet(), population.templates().keySet());
			return new PhantomPopulationEcologyCatalog(limits, personalities, presets, traitCodes, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
		}
		catch (IllegalArgumentException e)
		{
			throw e;
		}
		catch (Exception e)
		{
			throw new IllegalArgumentException("Could not load strict ecology catalog.", e);
		}
	}

	public PhantomPopulationEcologyState assign(Preset preset, long generation, long ordinal, long deterministicSeed, long nowEpochMinute, int worldAgeDaysOverride, long replacesProfileId, String fixedSchedule)
	{
		if ((generation < 1) || (ordinal < 1) || (deterministicSeed <= 0) || (nowEpochMinute < 0) || (worldAgeDaysOverride < -1) || (worldAgeDaysOverride > 3650) || (replacesProfileId < 0))
		{
			throw new IllegalArgumentException("Ecology assignment inputs are invalid.");
		}
		final PresetDefinition definition = requirePreset(preset);
		final AgeBand ageBand = weighted(definition.ages(), generation, ordinal, deterministicSeed, 11);
		final PaceEntry pace = weighted(definition.paces(), generation, ordinal, deterministicSeed, 23);
		final PersonalityEntry personality = weighted(definition.personalities(), generation, ordinal, deterministicSeed, 37);
		final ScheduleEntry schedule = fixedSchedule == null ? weighted(definition.schedules(), generation, ordinal, deterministicSeed, 53) : definition.schedules().stream().filter(value -> value.id().equals(fixedSchedule)).findFirst().orElseThrow(() -> new IllegalArgumentException("Existing population schedule is absent from ecology preset."));
		final long ageMinutes;
		if (replacesProfileId > 0)
		{
			ageMinutes = Math.floorMod(mix(deterministicSeed ^ replacesProfileId, ordinal), (definition.newcomerDays() * 1440L) + 1L);
		}
		else
		{
			final int maximumDays = worldAgeDaysOverride >= 0 ? Math.min(ageBand.maximumDays(), worldAgeDaysOverride) : ageBand.maximumDays();
			final int minimumDays = Math.min(ageBand.minimumDays(), maximumDays);
			final long minimumMinutes = minimumDays * 1440L;
			final long spanMinutes = ((maximumDays - minimumDays + 1L) * 1440L);
			ageMinutes = minimumMinutes + Math.floorMod(mix(deterministicSeed ^ 0x414745L, ordinal), spanMinutes);
		}
		final long join = Math.max(0, nowEpochMinute - ageMinutes);
		final long minimumEligible = Math.max(nowEpochMinute, Math.addExact(join, definition.archiveMinimumDays() * 1440L));
		final long delay = definition.archiveDelayDays() * 1440L;
		final long eligible = Math.max(minimumEligible, Math.addExact(nowEpochMinute, delay + Math.floorMod(mix(deterministicSeed ^ 0x41524348L, ordinal), delay + 1L)));
		return new PhantomPopulationEcologyState(_hash, preset, generation, ordinal, nowEpochMinute, join, join, nowEpochMinute, pace.pace(), pace.productiveShareBasisPoints(), _limits.productiveBlockMinutes(), personality.personality(), assignedTraits(personality.personality(), generation, ordinal, deterministicSeed), schedule.id(), Disposition.MANAGED, eligible, replacesProfileId, "", 0, 0, 0, "");
	}

	public boolean productive(PhantomPopulationEcologyState state, long blockOrdinal)
	{
		final long authoritySeed = Long.parseUnsignedLong(state.catalogHash().substring(0, 16), 16);
		final long offset = Math.floorMod(mix(authoritySeed ^ state.ecologyGeneration(), state.assignmentOrdinal() ^ state.pace().ordinal()), 10000L);
		final long slot = Math.floorMod(Math.floorMod(blockOrdinal, 10000L) * 7919L + offset, 10000L);
		return slot < state.productiveShareBasisPoints();
	}

	public NavigableMap<Integer, Integer> personalityTraits(PhantomPopulationEcologyState state, long profileId)
	{
		if (profileId <= 0)
		{
			throw new IllegalArgumentException("Ecology personality inputs are invalid.");
		}
		return Collections.unmodifiableNavigableMap(new TreeMap<>(state.initialSocialTraits()));
	}

	private NavigableMap<Integer, Integer> assignedTraits(Personality personality, long generation, long ordinal, long seed)
	{
		final PersonalityDefinition definition = Objects.requireNonNull(_personalities.get(personality));
		final NavigableMap<Integer, Integer> values = new TreeMap<>();
		for (TraitRange range : definition.traits().values())
		{
			final long mixed = mix(hashSeed() ^ generation ^ seed ^ ordinal, range.key().hashCode());
			final int delta = range.spread() == 0 ? 0 : (int) Math.floorMod(mixed, (range.spread() * 2L) + 1L) - range.spread();
			values.put(_socialTraitCodes.get(range.key()), Math.max(PhantomSocialModel.MIN_VALUE, Math.min(PhantomSocialModel.MAX_VALUE, range.center() + delta)));
		}
		return Collections.unmodifiableNavigableMap(values);
	}

	public PresetDefinition requirePreset(Preset preset)
	{
		final PresetDefinition definition = _presets.get(Objects.requireNonNull(preset, "preset"));
		if (definition == null)
		{
			throw new IllegalArgumentException("Unknown ecology preset.");
		}
		return definition;
	}

	public Limits limits()
	{
		return _limits;
	}

	public Map<Preset, PresetDefinition> presets()
	{
		return _presets;
	}

	public Map<Personality, PersonalityDefinition> personalities()
	{
		return _personalities;
	}

	public String hash()
	{
		return _hash;
	}

	private long hashSeed()
	{
		return Long.parseUnsignedLong(_hash.substring(0, 16), 16);
	}

	private static Map<Personality, PersonalityDefinition> parsePersonalities(Element section, Set<String> requiredTraits)
	{
		requireElement(section, "personalities");
		requireExactAttributes(section, Set.of());
		final Map<Personality, PersonalityDefinition> definitions = new EnumMap<>(Personality.class);
		for (Element element : childElements(section))
		{
			requireElement(element, "personality");
			requireExactAttributes(element, Set.of("id"));
			final Personality personality = exactEnum(Personality.class, element.getAttribute("id"), "personality");
			final Map<String, TraitRange> traits = new LinkedHashMap<>();
			for (Element trait : childElements(element))
			{
				requireElement(trait, "trait");
				requireExactAttributes(trait, Set.of("key", "center", "spread"));
				final String key = trait.getAttribute("key");
				final TraitRange range = new TraitRange(key, integer(trait, "center", -10000, 10000), integer(trait, "spread", 0, 10000));
				if (!requiredTraits.contains(key) || (Math.abs((long) range.center()) + range.spread() > 10000) || (traits.putIfAbsent(key, range) != null))
				{
					throw new IllegalArgumentException("Ecology personality trait mapping is invalid.");
				}
			}
			if (!traits.keySet().equals(requiredTraits) || (definitions.putIfAbsent(personality, new PersonalityDefinition(personality, Map.copyOf(traits))) != null))
			{
				throw new IllegalArgumentException("Ecology personality definitions must map every Social trait exactly once.");
			}
		}
		if (!definitions.keySet().equals(Set.of(Personality.values())))
		{
			throw new IllegalArgumentException("Ecology catalog must define every personality archetype.");
		}
		return definitions;
	}

	private static Map<Preset, PresetDefinition> parsePresets(Element section, Set<Personality> personalities, Set<String> schedules)
	{
		requireElement(section, "presets");
		requireExactAttributes(section, Set.of());
		final Map<Preset, PresetDefinition> definitions = new EnumMap<>(Preset.class);
		for (Element element : childElements(section))
		{
			requireElement(element, "preset");
			requireExactAttributes(element, Set.of("id", "defaultWorldAgeDays", "newcomerDays", "archiveMinimumDays", "archiveDelayDays"));
			final Preset preset = exactEnum(Preset.class, element.getAttribute("id"), "preset");
			final int defaultAge = integer(element, "defaultWorldAgeDays", 1, 3650);
			final int newcomerDays = integer(element, "newcomerDays", 0, 30);
			final int archiveMinimum = integer(element, "archiveMinimumDays", 1, 3650);
			final int archiveDelay = integer(element, "archiveDelayDays", 1, 3650);
			final List<Element> children = childElements(element);
			if ((children.size() != 4) || !"ages".equals(children.get(0).getTagName()) || !"paces".equals(children.get(1).getTagName()) || !"personalities".equals(children.get(2).getTagName()) || !"schedules".equals(children.get(3).getTagName()))
			{
				throw new IllegalArgumentException("Ecology preset sections are not in canonical order.");
			}
			final List<AgeBand> ages = parseAges(children.get(0), defaultAge);
			final List<PaceEntry> paces = parsePaces(children.get(1));
			final List<PersonalityEntry> personalityWeights = parsePersonalityWeights(children.get(2), personalities);
			final List<ScheduleEntry> scheduleWeights = parseSchedules(children.get(3), schedules);
			if (definitions.putIfAbsent(preset, new PresetDefinition(preset, defaultAge, newcomerDays, archiveMinimum, archiveDelay, ages, paces, personalityWeights, scheduleWeights)) != null)
			{
				throw new IllegalArgumentException("Duplicate ecology preset.");
			}
		}
		if (!definitions.keySet().equals(Set.of(Preset.values())))
		{
			throw new IllegalArgumentException("Ecology catalog must define FRESH, LIVING and MATURE.");
		}
		if (!((definitions.get(Preset.FRESH).defaultWorldAgeDays() < definitions.get(Preset.LIVING).defaultWorldAgeDays()) && (definitions.get(Preset.LIVING).defaultWorldAgeDays() < definitions.get(Preset.MATURE).defaultWorldAgeDays())))
		{
			throw new IllegalArgumentException("Ecology preset world ages must be strictly ordered.");
		}
		return definitions;
	}

	private static List<AgeBand> parseAges(Element section, int maximumDefaultAge)
	{
		requireElement(section, "ages");
		requireExactAttributes(section, Set.of());
		final List<AgeBand> result = new ArrayList<>();
		int previousMaximum = -1;
		for (Element element : childElements(section))
		{
			requireElement(element, "age");
			requireExactAttributes(element, Set.of("minimumDays", "maximumDays", "weight"));
			final AgeBand band = new AgeBand(integer(element, "minimumDays", 0, maximumDefaultAge), integer(element, "maximumDays", 0, maximumDefaultAge), integer(element, "weight", 1, 100));
			if ((band.minimumDays() > band.maximumDays()) || (band.minimumDays() <= previousMaximum))
			{
				throw new IllegalArgumentException("Ecology age bands must be ordered and non-overlapping.");
			}
			previousMaximum = band.maximumDays();
			result.add(band);
		}
		requireWeighted(result, 2, 16);
		return List.copyOf(result);
	}

	private static List<PaceEntry> parsePaces(Element section)
	{
		requireElement(section, "paces");
		requireExactAttributes(section, Set.of());
		final Map<Pace, PaceEntry> result = new EnumMap<>(Pace.class);
		for (Element element : childElements(section))
		{
			requireElement(element, "pace");
			requireExactAttributes(element, Set.of("id", "weight", "productiveShareBasisPoints"));
			final Pace pace = exactEnum(Pace.class, element.getAttribute("id"), "pace");
			if (result.putIfAbsent(pace, new PaceEntry(pace, integer(element, "weight", 1, 100), integer(element, "productiveShareBasisPoints", 1, 10000))) != null)
			{
				throw new IllegalArgumentException("Duplicate ecology pace.");
			}
		}
		if (!result.keySet().equals(Set.of(Pace.values())))
		{
			throw new IllegalArgumentException("Every ecology pace must be present in each preset.");
		}
		final List<PaceEntry> ordered = List.of(result.get(Pace.CASUAL), result.get(Pace.REGULAR), result.get(Pace.FAST), result.get(Pace.OUTLIER));
		requireWeighted(ordered, 4, 4);
		for (int index = 1; index < ordered.size(); index++)
		{
			if (ordered.get(index - 1).productiveShareBasisPoints() >= ordered.get(index).productiveShareBasisPoints())
			{
				throw new IllegalArgumentException("Ecology pace productive shares must be strictly increasing.");
			}
		}
		return ordered;
	}

	private static List<PersonalityEntry> parsePersonalityWeights(Element section, Set<Personality> personalities)
	{
		requireElement(section, "personalities");
		requireExactAttributes(section, Set.of());
		final Map<Personality, PersonalityEntry> values = new EnumMap<>(Personality.class);
		for (Element element : childElements(section))
		{
			requireElement(element, "personality");
			requireExactAttributes(element, Set.of("id", "weight"));
			final Personality personality = exactEnum(Personality.class, element.getAttribute("id"), "personality weight");
			if (!personalities.contains(personality) || (values.putIfAbsent(personality, new PersonalityEntry(personality, integer(element, "weight", 1, 100))) != null))
			{
				throw new IllegalArgumentException("Ecology personality weights are invalid.");
			}
		}
		if (!values.keySet().equals(personalities))
		{
			throw new IllegalArgumentException("Every ecology personality must be weighted in each preset.");
		}
		final List<PersonalityEntry> result = List.of(Personality.values()).stream().map(values::get).toList();
		requireWeighted(result, personalities.size(), personalities.size());
		return result;
	}

	private static List<ScheduleEntry> parseSchedules(Element section, Set<String> knownSchedules)
	{
		requireElement(section, "schedules");
		requireExactAttributes(section, Set.of());
		final Map<String, ScheduleEntry> values = new LinkedHashMap<>();
		for (Element element : childElements(section))
		{
			requireElement(element, "schedule");
			requireExactAttributes(element, Set.of("id", "weight"));
			final String id = element.getAttribute("id");
			if (!knownSchedules.contains(id) || (values.putIfAbsent(id, new ScheduleEntry(id, integer(element, "weight", 1, 100))) != null))
			{
				throw new IllegalArgumentException("Ecology schedule reference is unknown or duplicate.");
			}
		}
		final List<ScheduleEntry> result = List.copyOf(values.values());
		requireWeighted(result, 2, knownSchedules.size());
		return result;
	}

	private static <T extends Weighted> T weighted(List<T> values, long generation, long ordinal, long seed, int salt)
	{
		final long authority = mix(seed ^ generation, salt);
		final int multiplier = CYCLE_MULTIPLIERS[Math.floorMod((int) (authority >>> 17), CYCLE_MULTIPLIERS.length)];
		int slot = (int) Math.floorMod((Math.floorMod(ordinal - 1, WEIGHT_TOTAL) * multiplier) + Math.floorMod(authority, WEIGHT_TOTAL), WEIGHT_TOTAL);
		for (T value : values)
		{
			slot -= value.weight();
			if (slot < 0)
			{
				return value;
			}
		}
		throw new IllegalStateException("Ecology weighted cycle exhausted.");
	}

	private static void requireWeighted(List<? extends Weighted> values, int minimum, int maximum)
	{
		if ((values.size() < minimum) || (values.size() > maximum) || (values.stream().mapToInt(Weighted::weight).sum() != WEIGHT_TOTAL))
		{
			throw new IllegalArgumentException("Ecology weights must contain a bounded set totaling 100.");
		}
	}

	private static <T extends Enum<T>> T exactEnum(Class<T> type, String value, String label)
	{
		try
		{
			final T parsed = Enum.valueOf(type, value);
			if (!parsed.name().equals(value))
			{
				throw new IllegalArgumentException();
			}
			return parsed;
		}
		catch (RuntimeException e)
		{
			throw new IllegalArgumentException("Unknown exact ecology " + label + ".", e);
		}
	}

	private static int integer(Element element, String attribute, int minimum, int maximum)
	{
		final String value = element.getAttribute(attribute);
		if (!value.matches("-?[0-9]+"))
		{
			throw new IllegalArgumentException("Ecology integer attribute is invalid: " + attribute);
		}
		try
		{
			final int parsed = Integer.parseInt(value);
			if ((parsed < minimum) || (parsed > maximum))
			{
				throw new IllegalArgumentException("Ecology integer attribute is outside bounds: " + attribute);
			}
			return parsed;
		}
		catch (NumberFormatException e)
		{
			throw new IllegalArgumentException("Ecology integer attribute is invalid: " + attribute, e);
		}
	}

	private static List<Element> childElements(Element parent)
	{
		final List<Element> result = new ArrayList<>();
		for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling())
		{
			if (node instanceof Element element)
			{
				result.add(element);
			}
			else if ((node.getNodeType() == Node.TEXT_NODE) && !node.getTextContent().isBlank())
			{
				throw new IllegalArgumentException("Ecology catalog contains unexpected text.");
			}
		}
		return result;
	}

	private static void requireElement(Element element, String name)
	{
		if (!name.equals(element.getTagName()))
		{
			throw new IllegalArgumentException("Unexpected ecology element: " + element.getTagName());
		}
	}

	private static void requireExactAttributes(Element element, Set<String> expected)
	{
		final Set<String> actual = new HashSet<>();
		for (int index = 0; index < element.getAttributes().getLength(); index++)
		{
			actual.add(element.getAttributes().item(index).getNodeName());
		}
		if (!actual.equals(expected))
		{
			throw new IllegalArgumentException("Ecology element has unexpected attributes: " + element.getTagName());
		}
	}

	private static long mix(long seed, long value)
	{
		long mixed = seed ^ (value + 0x9E3779B97F4A7C15L);
		mixed = (mixed ^ (mixed >>> 30)) * 0xBF58476D1CE4E5B9L;
		mixed = (mixed ^ (mixed >>> 27)) * 0x94D049BB133111EBL;
		return mixed ^ (mixed >>> 31);
	}

	public record Limits(int productiveBlockMinutes, int maximumProfilesPerPulse, int maximumIntervalsPerPulse, int maximumCalendarDaysPerPulse)
	{
	}

	private interface Weighted
	{
		int weight();
	}

	public record AgeBand(int minimumDays, int maximumDays, int weight) implements Weighted
	{
	}

	public record PaceEntry(Pace pace, int weight, int productiveShareBasisPoints) implements Weighted
	{
	}

	public record PersonalityEntry(Personality personality, int weight) implements Weighted
	{
	}

	public record ScheduleEntry(String id, int weight) implements Weighted
	{
	}

	public record TraitRange(String key, int center, int spread)
	{
	}

	public record PersonalityDefinition(Personality personality, Map<String, TraitRange> traits)
	{
	}

	public record PresetDefinition(Preset preset, int defaultWorldAgeDays, int newcomerDays, int archiveMinimumDays, int archiveDelayDays, List<AgeBand> ages, List<PaceEntry> paces, List<PersonalityEntry> personalities, List<ScheduleEntry> schedules)
	{
	}
}
