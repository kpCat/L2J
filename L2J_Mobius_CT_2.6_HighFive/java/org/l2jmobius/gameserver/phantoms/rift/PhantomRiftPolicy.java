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
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.l2jmobius.gameserver.phantoms.party.PhantomPartyRoleCatalog;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.RoleRequirement;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/**
 * Phantom-only Rift composition/readiness policy. Canonical entry facts are
 * deliberately absent.
 */
public final class PhantomRiftPolicy
{
	private static final int MAX_BYTES = 64 * 1024;
	private final Limits _limits;
	private final Map<Integer, TierPolicy> _tiers;
	private final String _hash;

	private PhantomRiftPolicy(Limits limits, Map<Integer, TierPolicy> tiers, String hash)
	{
		_limits = limits;
		_tiers = Map.copyOf(tiers);
		_hash = hash;
	}

	public Limits limits()
	{
		return _limits;
	}

	public TierPolicy requireTier(int type)
	{
		final TierPolicy result = _tiers.get(type);
		if (result == null)
		{
			throw new IllegalArgumentException("Missing Phantom Rift policy tier: " + type);
		}
		return result;
	}

	public String hash()
	{
		return _hash;
	}

	public static PhantomRiftPolicy load(Path path, PhantomRiftCatalog catalog, PhantomPartyRoleCatalog roleCatalog)
	{
		try
		{
			final byte[] bytes = Files.readAllBytes(path);
			if ((bytes.length == 0) || (bytes.length > MAX_BYTES))
			{
				throw new IllegalArgumentException("Rift policy XML is outside bounds.");
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
			final Set<String> rootAttributes = Set.of("version", "candidateLimit", "candidateRange", "inviteTimeoutMillis", "refusalCooldownMillis", "maximumSeatAttempts", "maximumTotalAttempts", "regroupDistance", "minimumHpPercent", "minimumMpPercent", "minimumCpPercent", "levelOffsetBelowMobMinimum", "minimumEquippedItems", "requireWeapon", "physicalShotCount", "magicShotCount");
			requireElement(root, "riftPolicy", rootAttributes);
			if (!"1".equals(root.getAttribute("version")))
			{
				throw new IllegalArgumentException("Unknown Rift policy version.");
			}
			final Limits limits = new Limits(
				integer(root, "candidateLimit", 1, 32),
				integer(root, "candidateRange", 100, 10000),
				integer(root, "inviteTimeoutMillis", 15000, 60000),
				integer(root, "refusalCooldownMillis", 300000, 1800000),
				integer(root, "maximumSeatAttempts", 1, 8),
				integer(root, "maximumTotalAttempts", 1, 32),
				integer(root, "regroupDistance", 50, 5000),
				integer(root, "minimumHpPercent", 1, 100),
				integer(root, "minimumMpPercent", 0, 100),
				integer(root, "minimumCpPercent", 0, 100),
				integer(root, "levelOffsetBelowMobMinimum", 0, 10),
				integer(root, "minimumEquippedItems", 0, 20),
				bool(root, "requireWeapon"),
				integer(root, "physicalShotCount", 0, 100000),
				integer(root, "magicShotCount", 0, 100000));
			final Map<Integer, TierPolicy> tiers = new TreeMap<>();
			for (Element tierElement : childElements(root))
			{
				requireElement(tierElement, "tier", Set.of("type"));
				final int type = integer(tierElement, "type", 1, 6);
				catalog.requireTier(type);
				final List<VacancyPolicy> vacancies = new ArrayList<>();
				final Set<String> keys = new HashSet<>();
				int seats = 0;
				for (Element vacancy : childElements(tierElement))
				{
					requireElement(vacancy, "vacancy", Set.of("key", "role", "required", "minimumScore", "count", "priority"));
					final String key = PhantomRiftModel.requireKey(vacancy.getAttribute("key"), "Rift vacancy key");
					final String role = PhantomRiftModel.requireKey(vacancy.getAttribute("role"), "Rift vacancy role");
					if (!keys.add(key) || !roleCatalog.contains(role))
					{
						throw new IllegalArgumentException("Duplicate vacancy or unknown Goal 017 role.");
					}
					final int count = integer(vacancy, "count", 1, 9);
					seats = Math.addExact(seats, count);
					vacancies.add(new VacancyPolicy(key, role, bool(vacancy, "required"), integer(vacancy, "minimumScore", 1, 10000), count, integer(vacancy, "priority", 1, 10000)));
				}
				if (vacancies.isEmpty() || (seats > 9) || vacancies.stream().noneMatch(VacancyPolicy::required))
				{
					throw new IllegalArgumentException("Rift composition must define one to nine seats and a mandatory seat.");
				}
				if (tiers.put(type, new TierPolicy(type, vacancies)) != null)
				{
					throw new IllegalArgumentException("Duplicate Rift policy tier.");
				}
			}
			if (tiers.size() != 6)
			{
				throw new IllegalArgumentException("Rift policy must define all six factual tiers.");
			}
			return new PhantomRiftPolicy(limits, tiers, HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
		}
		catch (RuntimeException e)
		{
			throw e;
		}
		catch (Exception e)
		{
			throw new IllegalArgumentException("Could not load strict Phantom Rift policy.", e);
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
				throw new IllegalArgumentException("Unexpected text in Rift policy.");
			}
		}
		return result;
	}

	private static void requireElement(Element element, String name, Set<String> allowedAttributes)
	{
		if (!name.equals(element.getTagName()))
		{
			throw new IllegalArgumentException("Unknown Rift policy element.");
		}
		final NamedNodeMap attributes = element.getAttributes();
		if (attributes.getLength() != allowedAttributes.size())
		{
			throw new IllegalArgumentException("Unexpected Rift policy attribute count.");
		}
		for (int index = 0; index < attributes.getLength(); index++)
		{
			final Node attribute = attributes.item(index);
			if (!allowedAttributes.contains(attribute.getNodeName()) || attribute.getNodeValue().isBlank())
			{
				throw new IllegalArgumentException("Unknown or blank Rift policy attribute.");
			}
		}
	}

	private static int integer(Element element, String attribute, int minimum, int maximum)
	{
		final String value = element.getAttribute(attribute);
		if (!value.matches("[0-9]+"))
		{
			throw new IllegalArgumentException("Rift policy integer is invalid.");
		}
		final long parsed = Long.parseLong(value);
		if ((parsed < minimum) || (parsed > maximum))
		{
			throw new IllegalArgumentException("Rift policy integer is outside bounds.");
		}
		return (int) parsed;
	}

	private static boolean bool(Element element, String attribute)
	{
		return switch (element.getAttribute(attribute))
		{
			case "true" -> true;
			case "false" -> false;
			default -> throw new IllegalArgumentException("Rift policy boolean is invalid.");
		};
	}

	public record Limits(int candidateLimit, int candidateRange, int inviteTimeoutMillis, int refusalCooldownMillis, int maximumSeatAttempts, int maximumTotalAttempts, int regroupDistance, int minimumHpPercent, int minimumMpPercent, int minimumCpPercent, int levelOffsetBelowMobMinimum, int minimumEquippedItems, boolean requireWeapon, int physicalShotCount, int magicShotCount)
	{
	}

	public record VacancyPolicy(String key, String roleKey, boolean required, int minimumScore, int count, int priority)
	{
		public VacancyPolicy
		{
			key = PhantomRiftModel.requireKey(key, "Rift vacancy key");
			roleKey = PhantomRiftModel.requireKey(roleKey, "Rift role key");
		}

		public List<RoleRequirement> expand()
		{
			final List<RoleRequirement> result = new ArrayList<>(count);
			for (int index = 1; index <= count; index++)
			{
				result.add(new RoleRequirement(key + "." + index, roleKey, required, minimumScore));
			}
			return List.copyOf(result);
		}
	}

	public record TierPolicy(int type, List<VacancyPolicy> vacancies)
	{
		public TierPolicy
		{
			vacancies = vacancies.stream().sorted(Comparator.comparingInt(VacancyPolicy::priority).reversed().thenComparing(VacancyPolicy::key)).toList();
		}

		public List<RoleRequirement> requirements()
		{
			return vacancies.stream().flatMap(vacancy -> vacancy.expand().stream()).toList();
		}

		public VacancyPolicy policyFor(String expandedVacancyKey)
		{
			return vacancies.stream().filter(vacancy -> expandedVacancyKey.startsWith(vacancy.key() + ".")).findFirst().orElse(null);
		}
	}
}
