/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.siege;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Element;

/** Strict bounded policy; native schedule and registration truth are absent by design. */
public final class PhantomSiegeCatalog
{
	private static final int MAX_BYTES = 16 * 1024;
	private static final Set<String> ROOT_ATTRIBUTES = Set.of("id", "version");
	private static final Set<String> CASTLE_ATTRIBUTES = Set.of("id", "attackerStagingAnchor", "defenderStagingAnchor", "approachAnchor", "retreatAnchor", "maximumActiveParticipants", "maximumTargets", "combatDistance", "retreatHpPercent", "relevanceTtlSeconds", "combatTimeoutSeconds", "routeTimeoutSeconds");

	public record Strategy(int castleId, String attackerStagingAnchor, String defenderStagingAnchor, String approachAnchor, String retreatAnchor, int maximumActiveParticipants, int maximumTargets, int combatDistance, int retreatHpPercent, int relevanceTtlSeconds, int combatTimeoutSeconds, int routeTimeoutSeconds, String evidenceHash)
	{
		public Strategy
		{
			if ((castleId != 3) || !anchor(attackerStagingAnchor) || !anchor(defenderStagingAnchor) || !anchor(approachAnchor) || !anchor(retreatAnchor) || (maximumActiveParticipants < 2) || (maximumActiveParticipants > 16) || (maximumTargets < 1) || (maximumTargets > 8) || (combatDistance < 100) || (combatDistance > 2000) || (retreatHpPercent < 1) || (retreatHpPercent > 80) || (relevanceTtlSeconds < 30) || (relevanceTtlSeconds > 86400) || (combatTimeoutSeconds < 5) || (combatTimeoutSeconds > 300) || (routeTimeoutSeconds < 5) || (routeTimeoutSeconds > 300) || (evidenceHash == null) || !evidenceHash.matches("[0-9A-F]{64}"))
			{
				throw new IllegalArgumentException("Siege strategy is outside bounded Goal035 scope.");
			}
		}
	}

	private final String _id;
	private final String _hash;
	private final Strategy _strategy;

	private PhantomSiegeCatalog(String id, String hash, Strategy strategy)
	{
		_id = id;
		_hash = hash;
		_strategy = strategy;
	}

	public static PhantomSiegeCatalog load(Path path)
	{
		try
		{
			final byte[] bytes = Files.readAllBytes(path);
			if ((bytes.length == 0) || (bytes.length > MAX_BYTES))
			{
				throw new IllegalArgumentException("Siege policy size is outside bounds.");
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
			require(root, "siegePolicy", ROOT_ATTRIBUTES);
			if (!"1".equals(root.getAttribute("version")))
			{
				throw new IllegalArgumentException("Unsupported siege policy version.");
			}
			Element castle = null;
			for (int index = 0; index < root.getChildNodes().getLength(); index++)
			{
				if (root.getChildNodes().item(index) instanceof Element element)
				{
					if (castle != null)
					{
						throw new IllegalArgumentException("Siege policy must contain exactly one castle.");
					}
					castle = element;
				}
				else if (!root.getChildNodes().item(index).getTextContent().isBlank())
				{
					throw new IllegalArgumentException("Siege policy contains unexpected text.");
				}
			}
			if (castle == null)
			{
				throw new IllegalArgumentException("Siege policy castle is absent.");
			}
			require(castle, "castle", CASTLE_ATTRIBUTES);
			for (int index = 0; index < castle.getChildNodes().getLength(); index++)
			{
				if ((castle.getChildNodes().item(index) instanceof Element) || !castle.getChildNodes().item(index).getTextContent().isBlank())
				{
					throw new IllegalArgumentException("Siege castle policy contains content.");
				}
			}
			final String hash = HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
			final Strategy strategy = new Strategy(integer(castle, "id"), castle.getAttribute("attackerStagingAnchor"), castle.getAttribute("defenderStagingAnchor"), castle.getAttribute("approachAnchor"), castle.getAttribute("retreatAnchor"), integer(castle, "maximumActiveParticipants"), integer(castle, "maximumTargets"), integer(castle, "combatDistance"), integer(castle, "retreatHpPercent"), integer(castle, "relevanceTtlSeconds"), integer(castle, "combatTimeoutSeconds"), integer(castle, "routeTimeoutSeconds"), hash);
			return new PhantomSiegeCatalog(requireId(root.getAttribute("id")), hash, strategy);
		}
		catch (RuntimeException exception)
		{
			throw exception;
		}
		catch (Exception exception)
		{
			throw new IllegalArgumentException("Could not load strict siege policy.", exception);
		}
	}

	public String id()
	{
		return _id;
	}

	public String hash()
	{
		return _hash;
	}

	public Strategy strategy()
	{
		return _strategy;
	}

	public void validateTopologyAnchors(Predicate<String> anchorExists)
	{
		if (anchorExists == null)
		{
			throw new IllegalArgumentException("Siege topology authority is absent.");
		}
		for (String anchorId : List.of(_strategy.attackerStagingAnchor(), _strategy.defenderStagingAnchor(), _strategy.approachAnchor(), _strategy.retreatAnchor()))
		{
			if (!anchorExists.test(anchorId))
			{
				throw new IllegalArgumentException("Siege topology anchor is absent: " + anchorId);
			}
		}
	}

	private static int integer(Element element, String name)
	{
		final String value = element.getAttribute(name);
		if (!value.matches("[1-9][0-9]{0,7}"))
		{
			throw new IllegalArgumentException("Siege policy integer is not canonical: " + name);
		}
		return Integer.parseInt(value);
	}

	private static void require(Element element, String name, Set<String> attributes)
	{
		if (!name.equals(element.getTagName()) || (element.getAttributes().getLength() != attributes.size()))
		{
			throw new IllegalArgumentException("Siege policy element is not exact: " + name);
		}
		for (String attribute : attributes)
		{
			if (!element.hasAttribute(attribute))
			{
				throw new IllegalArgumentException("Siege policy attribute is absent: " + attribute);
			}
		}
	}

	private static String requireId(String value)
	{
		if ((value == null) || !value.matches("[a-z][a-z0-9_.-]{0,63}"))
		{
			throw new IllegalArgumentException("Invalid siege policy ID.");
		}
		return value;
	}

	private static boolean anchor(String value)
	{
		return (value != null) && value.matches("[a-z][a-z0-9_.-]{0,95}");
	}
}
