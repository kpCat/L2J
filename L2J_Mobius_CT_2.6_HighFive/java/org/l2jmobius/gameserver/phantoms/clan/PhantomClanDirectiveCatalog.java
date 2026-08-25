/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.clan;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.l2jmobius.gameserver.phantoms.clan.PhantomClanDirectiveModel.Definition;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanDirectiveModel.Effect;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanDirectiveModel.Kind;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public final class PhantomClanDirectiveCatalog
{
	private static final int MAX_BYTES = 32 * 1024;
	private static final int MAX_KINDS = 8;
	private static final int MAX_ALIASES = 64;
	private static final int MAX_ALIAS_CODE_POINTS = 48;
	private static final Set<Kind> REQUIRED_KINDS = Set.of(Kind.ASSEMBLE, Kind.STANDBY, Kind.DISMISS);
	private final Map<Kind, Definition> _definitions;
	private final Map<String, Definition> _aliases;
	private final String _hash;

	private PhantomClanDirectiveCatalog(Map<Kind, Definition> definitions, Map<String, Definition> aliases, String hash)
	{
		_definitions = Collections.unmodifiableMap(new EnumMap<>(definitions));
		_aliases = Map.copyOf(aliases);
		_hash = hash;
	}

	public static PhantomClanDirectiveCatalog load(Path path)
	{
		try
		{
			final byte[] bytes = Files.readAllBytes(path);
			if ((bytes.length == 0) || (bytes.length > MAX_BYTES))
			{
				throw new IllegalArgumentException("Clan directive catalog size is outside bounds.");
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
			requireElement(root, "clanDirectiveCatalog", List.of("version"));
			if (!"1".equals(root.getAttribute("version")))
			{
				throw new IllegalArgumentException("Unknown clan directive catalog version.");
			}
			final List<Element> sections = childElements(root);
			if ((sections.size() != 1) || !"directives".equals(sections.get(0).getTagName()))
			{
				throw new IllegalArgumentException("Clan directive catalog sections are missing, duplicated or out of order.");
			}
			final Element directivesElement = sections.get(0);
			requireElement(directivesElement, "directives", List.of());
			final EnumMap<Kind, Definition> definitions = new EnumMap<>(Kind.class);
			final Map<String, Definition> aliases = new HashMap<>();
			int aliasCount = 0;
			for (Element directiveElement : children(directivesElement, "directive"))
			{
				requireElement(directiveElement, "directive", List.of("kind", "baseScore", "effect", "ttlSeconds"));
				final Kind kind = enumValue(Kind.class, directiveElement.getAttribute("kind"), "directive kind");
				final Effect effect = enumValue(Effect.class, directiveElement.getAttribute("effect"), "directive effect");
				final int baseScore = strictInt(directiveElement.getAttribute("baseScore"), -3000, 3000);
				final int ttlSeconds = strictInt(directiveElement.getAttribute("ttlSeconds"), 0, 86400);
				final List<String> normalizedAliases = new ArrayList<>();
				for (Element aliasElement : children(directiveElement, "alias"))
				{
					final String normalized = aliasText(aliasElement);
					if (normalizedAliases.contains(normalized))
					{
						throw new IllegalArgumentException("Duplicate alias inside one clan directive.");
					}
					normalizedAliases.add(normalized);
				}
				final Definition definition = new Definition(kind, baseScore, effect, Math.multiplyExact(ttlSeconds, 1000L), normalizedAliases);
				if (definitions.putIfAbsent(kind, definition) != null)
				{
					throw new IllegalArgumentException("Duplicate clan directive kind.");
				}
				for (String alias : normalizedAliases)
				{
					if (aliases.putIfAbsent(alias, definition) != null)
					{
						throw new IllegalArgumentException("Ambiguous clan directive alias.");
					}
					aliasCount++;
				}
			}
			if (!definitions.keySet().equals(REQUIRED_KINDS) || (definitions.size() > MAX_KINDS) || (aliasCount > MAX_ALIASES))
			{
				throw new IllegalArgumentException("Required clan directives are incomplete or excessive.");
			}
			validateTuning(definitions);
			final String hash = HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
			return new PhantomClanDirectiveCatalog(definitions, aliases, hash);
		}
		catch (RuntimeException exception)
		{
			throw exception;
		}
		catch (Exception exception)
		{
			throw new IllegalArgumentException("Could not load strict clan directive catalog.", exception);
		}
	}

	public String hash()
	{
		return _hash;
	}

	public List<Definition> directives()
	{
		return List.copyOf(_definitions.values());
	}

	public Definition require(Kind kind)
	{
		final Definition definition = _definitions.get(kind);
		if (definition == null)
		{
			throw new IllegalArgumentException("Unknown clan directive kind.");
		}
		return definition;
	}

	public Optional<Definition> parse(String text)
	{
		final String normalized = normalize(text);
		return normalized.isEmpty() ? Optional.empty() : Optional.ofNullable(_aliases.get(normalized));
	}

	public static String normalize(String input)
	{
		if (input == null)
		{
			return "";
		}
		final String value = Normalizer.normalize(input, Normalizer.Form.NFKC);
		if ((value.codePointCount(0, value.length()) > 128) || (value.getBytes(StandardCharsets.UTF_8).length > 512))
		{
			return "";
		}
		final StringBuilder result = new StringBuilder();
		boolean separator = false;
		for (int offset = 0; offset < value.length();)
		{
			int codePoint = value.codePointAt(offset);
			offset += Character.charCount(codePoint);
			codePoint = Character.toLowerCase(codePoint);
			if (codePoint == 'ё')
			{
				codePoint = 'е';
			}
			if (Character.isLetterOrDigit(codePoint))
			{
				if (separator && !result.isEmpty())
				{
					result.append(' ');
				}
				result.appendCodePoint(codePoint);
				separator = false;
				if (result.codePointCount(0, result.length()) > MAX_ALIAS_CODE_POINTS)
				{
					return "";
				}
			}
			else if (Character.isWhitespace(codePoint) || isPunctuation(codePoint))
			{
				separator = !result.isEmpty();
			}
			else
			{
				return "";
			}
		}
		return result.toString();
	}

	private static boolean isPunctuation(int codePoint)
	{
		return switch (Character.getType(codePoint))
		{
			case Character.CONNECTOR_PUNCTUATION,
				Character.DASH_PUNCTUATION,
				Character.START_PUNCTUATION,
				Character.END_PUNCTUATION,
				Character.INITIAL_QUOTE_PUNCTUATION,
				Character.FINAL_QUOTE_PUNCTUATION,
				Character.OTHER_PUNCTUATION -> true;
			default -> false;
		};
	}

	private static String aliasText(Element element)
	{
		requireElement(element, "alias", List.of());
		for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling())
		{
			if ((child.getNodeType() != Node.TEXT_NODE) && (child.getNodeType() != Node.CDATA_SECTION_NODE))
			{
				throw new IllegalArgumentException("Clan directive alias contains unsupported content.");
			}
		}
		final String normalized = normalize(element.getTextContent());
		if (normalized.isEmpty())
		{
			throw new IllegalArgumentException("Clan directive alias is empty or unsupported.");
		}
		return normalized;
	}

	private static void validateTuning(Map<Kind, Definition> definitions)
	{
		requireTuning(definitions.get(Kind.ASSEMBLE), 600, Effect.ACTIVE, 120_000, Set.of("сбор", "го сбор", "сбор клана", "все на сбор", "онлайн на сбор", "sbor", "go sbor"));
		requireTuning(definitions.get(Kind.STANDBY), 250, Effect.WARM, 300_000, Set.of("готовность", "будьте готовы", "держим онлайн", "standby"));
		requireTuning(definitions.get(Kind.DISMISS), 1000, Effect.WITHDRAW, 0, Set.of("отбой", "расходимся", "сбор окончен", "otboy"));
	}

	private static void requireTuning(Definition definition, int baseScore, Effect effect, long ttlMillis, Set<String> aliases)
	{
		if ((definition == null) || (definition.baseScore() != baseScore) || (definition.effect() != effect) || (definition.ttlMillis() != ttlMillis) || !new HashSet<>(definition.aliases()).containsAll(aliases))
		{
			throw new IllegalArgumentException("Required clan directive tuning or aliases are incomplete.");
		}
	}

	private static <E extends Enum<E>> E enumValue(Class<E> type, String value, String label)
	{
		try
		{
			return Enum.valueOf(type, value);
		}
		catch (RuntimeException exception)
		{
			throw new IllegalArgumentException("Unknown clan " + label + ".", exception);
		}
	}

	private static int strictInt(String value, int minimum, int maximum)
	{
		if ((value == null) || value.isEmpty())
		{
			throw new IllegalArgumentException("Invalid clan directive integer.");
		}
		int index = 0;
		boolean negative = false;
		if (value.charAt(0) == '-')
		{
			negative = true;
			index = 1;
		}
		if (index == value.length())
		{
			throw new IllegalArgumentException("Invalid clan directive integer.");
		}
		long result = 0;
		for (; index < value.length(); index++)
		{
			final char digit = value.charAt(index);
			if ((digit < '0') || (digit > '9'))
			{
				throw new IllegalArgumentException("Invalid clan directive integer.");
			}
			result = (result * 10) + (digit - '0');
			if (result > Integer.MAX_VALUE)
			{
				throw new IllegalArgumentException("Clan directive integer is outside bounds.");
			}
		}
		final int parsed = (int) (negative ? -result : result);
		if ((parsed < minimum) || (parsed > maximum))
		{
			throw new IllegalArgumentException("Clan directive integer is outside bounds.");
		}
		return parsed;
	}

	private static List<Element> children(Element parent, String name)
	{
		final List<Element> result = childElements(parent);
		if (result.stream().anyMatch(element -> !name.equals(element.getTagName())))
		{
			throw new IllegalArgumentException("Unknown clan directive catalog element.");
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
				throw new IllegalArgumentException("Unexpected text in clan directive catalog.");
			}
		}
		return result;
	}

	private static void requireElement(Element element, String name, List<String> attributes)
	{
		if (!name.equals(element.getTagName()) || (element.getAttributes().getLength() != attributes.size()))
		{
			throw new IllegalArgumentException("Invalid clan directive catalog element.");
		}
		for (String attribute : attributes)
		{
			if (!element.hasAttribute(attribute) || element.getAttribute(attribute).isBlank())
			{
				throw new IllegalArgumentException("Missing clan directive catalog attribute.");
			}
		}
	}
}
