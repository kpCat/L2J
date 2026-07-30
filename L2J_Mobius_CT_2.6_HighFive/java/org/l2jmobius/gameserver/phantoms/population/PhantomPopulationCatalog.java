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
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.l2jmobius.gameserver.phantoms.population;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.zone.ZoneOffsetTransition;
import java.time.zone.ZoneRules;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.l2jmobius.gameserver.model.actor.enums.player.PlayerClass;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Strict bounded data catalog for deterministic managed population identities
 * and weekly activity schedules.
 */
public final class PhantomPopulationCatalog
{
	public static final int MAX_BYTES = 262_144;
	public static final int MAX_NAMES = 256;
	public static final int MAX_CLASSES = 64;
	public static final int MAX_TEMPLATES = 64;
	public static final int MAX_WINDOWS_PER_TEMPLATE = 128;
	private static final int MINUTES_PER_WEEK = 7 * 24 * 60;

	private final List<String> _prefixes;
	private final List<String> _suffixes;
	private final List<ClassEntry> _classes;
	private final Map<String, ScheduleTemplate> _templates;
	private final String _hash;

	private PhantomPopulationCatalog(List<String> prefixes, List<String> suffixes, List<ClassEntry> classes, Map<String, ScheduleTemplate> templates, String hash)
	{
		_prefixes = List.copyOf(prefixes);
		_suffixes = List.copyOf(suffixes);
		_classes = List.copyOf(classes);
		_templates = Collections.unmodifiableMap(new LinkedHashMap<>(templates));
		_hash = hash;
	}

	public static PhantomPopulationCatalog load(Path path, ZoneId zoneId)
	{
		Objects.requireNonNull(path, "Catalog path must not be null.");
		Objects.requireNonNull(zoneId, "Schedule time zone must not be null.");
		try
		{
			final byte[] bytes = Files.readAllBytes(path);
			if ((bytes.length == 0) || (bytes.length > MAX_BYTES))
			{
				throw new IllegalArgumentException("Population catalog must contain 1..262144 bytes.");
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
			final Document document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
			final Element root = document.getDocumentElement();
			requireElement(root, "populationCatalog");
			requireExactAttributes(root, Set.of("version"));
			if (!"1".equals(root.getAttribute("version")))
			{
				throw new IllegalArgumentException("Population catalog version must be 1.");
			}
			final List<Element> rootChildren = childElements(root);
			if ((rootChildren.size() != 3) || !"names".equals(rootChildren.get(0).getTagName()) || !"classes".equals(rootChildren.get(1).getTagName()) || !"schedules".equals(rootChildren.get(2).getTagName()))
			{
				throw new IllegalArgumentException("Population catalog must contain names, classes and schedules in canonical order.");
			}
			final Names names = parseNames(rootChildren.get(0));
			final List<ClassEntry> classes = parseClasses(rootChildren.get(1));
			final Map<String, ScheduleTemplate> templates = parseSchedules(rootChildren.get(2), zoneId);
			final String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
			return new PhantomPopulationCatalog(names.prefixes(), names.suffixes(), classes, templates, hash);
		}
		catch (IllegalArgumentException e)
		{
			throw e;
		}
		catch (Exception e)
		{
			throw new IllegalArgumentException("Could not load strict population catalog.", e);
		}
	}

	public String name(long deterministicValue, int attempt)
	{
		if ((attempt < 0) || (attempt > 255))
		{
			throw new IllegalArgumentException("Name inputs are outside bounded deterministic range.");
		}
		final String prefix = _prefixes.get(index(deterministicValue + attempt, _prefixes.size()));
		final String suffix = _suffixes.get(index(Long.rotateLeft(deterministicValue, 17) + attempt, _suffixes.size()));
		final String discriminator = Long.toUnsignedString(deterministicValue + attempt, 36);
		final String candidate = prefix + suffix + discriminator.substring(Math.max(0, discriminator.length() - 3));
		return candidate.substring(0, Math.min(16, candidate.length()));
	}

	public ClassEntry chooseClass(long deterministicValue)
	{
		return weighted(_classes, deterministicValue, ClassEntry::weight);
	}

	public ScheduleTemplate chooseSchedule(long deterministicValue)
	{
		return weighted(new ArrayList<>(_templates.values()), deterministicValue, ScheduleTemplate::weight);
	}

	public ScheduleEvaluation evaluate(String templateId, Instant now, ZoneId zoneId, int phaseMinutes)
	{
		final ScheduleTemplate template = _templates.get(templateId);
		if (template == null)
		{
			throw new IllegalArgumentException("Unknown population schedule template: " + templateId);
		}
		if ((phaseMinutes < -template.maximumPhaseMinutes()) || (phaseMinutes > template.maximumPhaseMinutes()))
		{
			throw new IllegalArgumentException("Schedule phase is outside template bounds.");
		}
		final ZonedDateTime shifted = Objects.requireNonNull(now, "Current instant must not be null.").atZone(zoneId).minusMinutes(phaseMinutes);
		final PhantomActivityState state = stateAt(template, shifted.getDayOfWeek(), shifted.toLocalTime());
		Instant next = null;
		final LocalDate shiftedDate = shifted.toLocalDate();
		for (int dayOffset = -1; dayOffset <= 8; dayOffset++)
		{
			final LocalDate date = shiftedDate.plusDays(dayOffset);
			for (ScheduleWindow window : template.windows())
			{
				if (!window.days().contains(date.getDayOfWeek()))
				{
					continue;
				}
				next = earlierFuture(next, resolve(date.atTime(window.start()).plusMinutes(phaseMinutes), zoneId), now);
				final LocalDate endDate = !window.end().isAfter(window.start()) ? date.plusDays(1) : date;
				next = earlierFuture(next, resolve(endDate.atTime(window.end()).plusMinutes(phaseMinutes), zoneId), now);
			}
		}
		if (next == null)
		{
			throw new IllegalStateException("Schedule has no future boundary.");
		}
		return new ScheduleEvaluation(state, next);
	}

	public List<ClassEntry> classes()
	{
		return _classes;
	}

	public Map<String, ScheduleTemplate> templates()
	{
		return _templates;
	}

	public String hash()
	{
		return _hash;
	}

	private static Names parseNames(Element element)
	{
		requireElement(element, "names");
		requireExactAttributes(element, Set.of());
		final List<String> prefixes = new ArrayList<>();
		final List<String> suffixes = new ArrayList<>();
		for (Element child : childElements(element))
		{
			requireExactAttributes(child, Set.of("value"));
			final String value = child.getAttribute("value");
			if (!value.matches("[A-Za-z][A-Za-z0-9]{0,7}"))
			{
				throw new IllegalArgumentException("Population name fragment must be 1..8 ASCII alphanumeric characters.");
			}
			if ("prefix".equals(child.getTagName()))
			{
				prefixes.add(value);
			}
			else if ("suffix".equals(child.getTagName()))
			{
				suffixes.add(value);
			}
			else
			{
				throw new IllegalArgumentException("Unexpected element in population names: " + child.getTagName());
			}
		}
		requireUniqueBounded(prefixes, "name prefixes");
		requireUniqueBounded(suffixes, "name suffixes");
		return new Names(prefixes, suffixes);
	}

	private static List<ClassEntry> parseClasses(Element element)
	{
		requireElement(element, "classes");
		requireExactAttributes(element, Set.of());
		final List<ClassEntry> entries = new ArrayList<>();
		final Set<Integer> ids = new HashSet<>();
		for (Element child : childElements(element))
		{
			requireElement(child, "class");
			requireExactAttributes(child, Set.of("id", "sex", "weight"));
			final int classId = boundedInteger(child.getAttribute("id"), 0, 255, "class ID");
			final PlayerClass playerClass = PlayerClass.getPlayerClass(classId);
			if ((playerClass == null) || (playerClass.level() != 0) || !ids.add(classId))
			{
				throw new IllegalArgumentException("Population classes must be unique canonical starting classes.");
			}
			entries.add(new ClassEntry(classId, SexPolicy.valueOf(child.getAttribute("sex")), boundedInteger(child.getAttribute("weight"), 1, 1_000_000, "class weight")));
		}
		if ((entries.isEmpty()) || (entries.size() > MAX_CLASSES))
		{
			throw new IllegalArgumentException("Population catalog must contain 1..64 starting classes.");
		}
		final Set<Integer> expected = new HashSet<>();
		for (PlayerClass playerClass : PlayerClass.values())
		{
			if (playerClass.level() == 0)
			{
				expected.add(playerClass.getId());
			}
		}
		if (!ids.equals(expected))
		{
			throw new IllegalArgumentException("Population catalog must cover every canonical level-zero starting class exactly once.");
		}
		return List.copyOf(entries);
	}

	private static Map<String, ScheduleTemplate> parseSchedules(Element element, ZoneId zoneId)
	{
		requireElement(element, "schedules");
		requireExactAttributes(element, Set.of());
		final Map<String, ScheduleTemplate> templates = new LinkedHashMap<>();
		for (Element child : childElements(element))
		{
			requireElement(child, "template");
			requireExactAttributes(child, Set.of("id", "weight", "maxPhaseMinutes"));
			final String id = child.getAttribute("id");
			if (!id.matches("[a-z][a-z0-9_.-]{0,31}") || templates.containsKey(id))
			{
				throw new IllegalArgumentException("Schedule template IDs must be unique bounded decision keys.");
			}
			final int weight = boundedInteger(child.getAttribute("weight"), 1, 1_000_000, "schedule weight");
			final int maximumPhase = boundedInteger(child.getAttribute("maxPhaseMinutes"), 0, 240, "maximum phase");
			final List<ScheduleWindow> windows = new ArrayList<>();
			final boolean[] occupied = new boolean[MINUTES_PER_WEEK];
			for (Element windowElement : childElements(child))
			{
				requireElement(windowElement, "window");
				requireExactAttributes(windowElement, Set.of("days", "start", "end", "state"));
				final EnumSet<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
				for (String token : windowElement.getAttribute("days").split(","))
				{
					if (!days.add(DayOfWeek.valueOf(token)))
					{
						throw new IllegalArgumentException("Schedule window contains a duplicate day.");
					}
				}
				if (days.isEmpty())
				{
					throw new IllegalArgumentException("Schedule window must contain at least one day.");
				}
				final LocalTime start = LocalTime.parse(windowElement.getAttribute("start"));
				final LocalTime end = LocalTime.parse(windowElement.getAttribute("end"));
				if (start.equals(end) || (start.getSecond() != 0) || (start.getNano() != 0) || (end.getSecond() != 0) || (end.getNano() != 0))
				{
					throw new IllegalArgumentException("Schedule windows must have distinct minute-aligned endpoints.");
				}
				final PhantomActivityState state = PhantomActivityState.valueOf(windowElement.getAttribute("state"));
				if ((state != PhantomActivityState.ACTIVE) && (state != PhantomActivityState.WARM) && (state != PhantomActivityState.BACKGROUND))
				{
					throw new IllegalArgumentException("Schedule window state must be ACTIVE, WARM or BACKGROUND.");
				}
				final ScheduleWindow window = new ScheduleWindow(Set.copyOf(days), start, end, state);
				markOccupied(occupied, window);
				windows.add(window);
			}
			if (windows.isEmpty() || (windows.size() > MAX_WINDOWS_PER_TEMPLATE))
			{
				throw new IllegalArgumentException("Schedule template must contain 1..128 windows.");
			}
			final ScheduleTemplate template = new ScheduleTemplate(id, weight, maximumPhase, List.copyOf(windows));
			templates.put(id, template);
			evaluateZoneCompatibility(template, zoneId);
		}
		if (templates.isEmpty() || (templates.size() > MAX_TEMPLATES))
		{
			throw new IllegalArgumentException("Population catalog must contain 1..64 schedule templates.");
		}
		return Collections.unmodifiableMap(new LinkedHashMap<>(templates));
	}

	private static void markOccupied(boolean[] occupied, ScheduleWindow window)
	{
		for (DayOfWeek day : window.days())
		{
			final int start = ((day.getValue() - 1) * 1440) + (window.start().getHour() * 60) + window.start().getMinute();
			int end = ((day.getValue() - 1) * 1440) + (window.end().getHour() * 60) + window.end().getMinute();
			if (!window.end().isAfter(window.start()))
			{
				end += 1440;
			}
			for (int minute = start; minute < end; minute++)
			{
				final int index = minute % MINUTES_PER_WEEK;
				if (occupied[index])
				{
					throw new IllegalArgumentException("Population schedule windows overlap.");
				}
				occupied[index] = true;
			}
		}
	}

	private static PhantomActivityState stateAt(ScheduleTemplate template, DayOfWeek day, LocalTime time)
	{
		for (ScheduleWindow window : template.windows())
		{
			if (window.end().isAfter(window.start()))
			{
				if (window.days().contains(day) && !time.isBefore(window.start()) && time.isBefore(window.end()))
				{
					return window.state();
				}
			}
			else if ((window.days().contains(day) && !time.isBefore(window.start())) || (window.days().contains(previous(day)) && time.isBefore(window.end())))
			{
				return window.state();
			}
		}
		return PhantomActivityState.SLEEPING;
	}

	private static DayOfWeek previous(DayOfWeek day)
	{
		return DayOfWeek.of(day.getValue() == 1 ? 7 : day.getValue() - 1);
	}

	private static Instant resolve(LocalDateTime local, ZoneId zoneId)
	{
		final ZoneRules rules = zoneId.getRules();
		final List<ZoneOffset> offsets = rules.getValidOffsets(local);
		if (offsets.size() == 1)
		{
			return local.toInstant(offsets.get(0));
		}
		if (offsets.size() == 2)
		{
			return local.toInstant(offsets.get(0));
		}
		final ZoneOffsetTransition transition = rules.getTransition(local);
		return transition.getDateTimeAfter().toInstant(transition.getOffsetAfter());
	}

	private static Instant earlierFuture(Instant current, Instant candidate, Instant now)
	{
		return candidate.isAfter(now) && ((current == null) || candidate.isBefore(current)) ? candidate : current;
	}

	private static void evaluateZoneCompatibility(ScheduleTemplate template, ZoneId zoneId)
	{
		final Instant probe = Instant.parse("2026-01-01T00:00:00Z");
		final PhantomPopulationCatalog temporary = new PhantomPopulationCatalog(List.of("A"), List.of("B"), List.of(), Map.of(template.id(), template), "");
		temporary.evaluate(template.id(), probe, zoneId, 0);
	}

	private static <T> T weighted(List<T> values, long deterministicValue, java.util.function.ToIntFunction<T> weight)
	{
		long total = 0;
		for (T value : values)
		{
			total = Math.addExact(total, weight.applyAsInt(value));
		}
		long selected = Math.floorMod(deterministicValue, total);
		for (T value : values)
		{
			selected -= weight.applyAsInt(value);
			if (selected < 0)
			{
				return value;
			}
		}
		throw new IllegalStateException("Weighted selection exhausted.");
	}

	private static int index(long value, int size)
	{
		return (int) Math.floorMod(value, size);
	}

	private static void requireUniqueBounded(List<String> values, String label)
	{
		if (values.isEmpty() || (values.size() > MAX_NAMES) || (new HashSet<>(values).size() != values.size()))
		{
			throw new IllegalArgumentException("Population " + label + " must contain 1..256 unique entries.");
		}
	}

	private static int boundedInteger(String value, int minimum, int maximum, String label)
	{
		if ((value == null) || !value.matches("[0-9]+"))
		{
			throw new IllegalArgumentException("Population " + label + " must be an unsigned decimal integer.");
		}
		final int parsed = Integer.parseInt(value);
		if ((parsed < minimum) || (parsed > maximum))
		{
			throw new IllegalArgumentException("Population " + label + " is outside its bounded range.");
		}
		return parsed;
	}

	private static void requireElement(Element element, String expected)
	{
		if ((element == null) || !expected.equals(element.getTagName()))
		{
			throw new IllegalArgumentException("Expected population catalog element: " + expected);
		}
	}

	private static List<Element> childElements(Element parent)
	{
		final List<Element> children = new ArrayList<>();
		final NodeList nodes = parent.getChildNodes();
		for (int i = 0; i < nodes.getLength(); i++)
		{
			final Node node = nodes.item(i);
			if (node instanceof Element element)
			{
				children.add(element);
			}
			else if ((node.getNodeType() == Node.TEXT_NODE) && !node.getTextContent().isBlank())
			{
				throw new IllegalArgumentException("Population catalog contains unexpected text.");
			}
		}
		return children;
	}

	private static void requireExactAttributes(Element element, Set<String> expected)
	{
		final Set<String> actual = new HashSet<>();
		for (int i = 0; i < element.getAttributes().getLength(); i++)
		{
			actual.add(element.getAttributes().item(i).getNodeName());
		}
		if (!actual.equals(expected))
		{
			throw new IllegalArgumentException("Unexpected attributes on population catalog element " + element.getTagName() + ".");
		}
	}

	public enum SexPolicy
	{
		BOTH,
		MALE,
		FEMALE;

		public boolean female(long deterministicValue)
		{
			return switch (this)
			{
				case BOTH -> (deterministicValue & 1) != 0;
				case MALE -> false;
				case FEMALE -> true;
			};
		}
	}

	public record ClassEntry(int classId, SexPolicy sex, int weight)
	{
		public ClassEntry
		{
			Objects.requireNonNull(sex, "Class sex policy must not be null.");
		}
	}

	public record ScheduleTemplate(String id, int weight, int maximumPhaseMinutes, List<ScheduleWindow> windows)
	{
	}

	public record ScheduleWindow(Set<DayOfWeek> days, LocalTime start, LocalTime end, PhantomActivityState state)
	{
	}

	public record ScheduleEvaluation(PhantomActivityState state, Instant nextBoundary)
	{
	}

	private record Names(List<String> prefixes, List<String> suffixes)
	{
	}
}
