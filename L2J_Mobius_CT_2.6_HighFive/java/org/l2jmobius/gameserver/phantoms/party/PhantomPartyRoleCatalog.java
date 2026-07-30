/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.party;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.ObjectiveMode;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.RoleDefinition;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Immutable, strict and content-addressed role-to-capability mapping.
 */
public final class PhantomPartyRoleCatalog
{
	private static final int MAX_BYTES = 64 * 1024;
	private final Map<String, RoleDefinition> _roles;
	private final String _hash;

	public PhantomPartyRoleCatalog(Map<String, RoleDefinition> roles, String hash)
	{
		if ((roles == null) || roles.isEmpty() || (roles.size() > 32))
		{
			throw new IllegalArgumentException("Party role catalog must contain one to 32 roles.");
		}
		_roles = Map.copyOf(roles);
		_hash = Objects.requireNonNull(hash);
	}

	public RoleDefinition require(String roleKey)
	{
		final RoleDefinition result = _roles.get(roleKey);
		if (result == null)
		{
			throw new IllegalArgumentException("Unknown party role key: " + roleKey);
		}
		return result;
	}

	public boolean contains(String roleKey)
	{
		return _roles.containsKey(roleKey);
	}

	public String hash()
	{
		return _hash;
	}

	public static PhantomPartyRoleCatalog load(Path path)
	{
		try
		{
			final byte[] bytes = Files.readAllBytes(path);
			if ((bytes.length == 0) || (bytes.length > MAX_BYTES))
			{
				throw new IllegalArgumentException("Party role catalog size is outside bounds.");
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
			requireElement(root, "partyRoles", List.of("version"));
			if (!"1".equals(root.getAttribute("version")))
			{
				throw new IllegalArgumentException("Unknown party role catalog version.");
			}
			final Map<String, RoleDefinition> roles = new HashMap<>();
			for (Element role : children(root, "role"))
			{
				requireElement(role, "role", List.of("key", "support"));
				final String roleKey = role.getAttribute("key");
				final boolean support = strictBoolean(role.getAttribute("support"));
				final Map<String, Integer> capabilities = new HashMap<>();
				final Map<ObjectiveMode, Integer> objectives = new EnumMap<>(ObjectiveMode.class);
				for (Element child : childElements(role))
				{
					switch (child.getTagName())
					{
						case "capability":
						{
							requireElement(child, "capability", List.of("key", "weight"));
							if (capabilities.put(child.getAttribute("key"), strictInt(child.getAttribute("weight"), 1, 1000)) != null)
							{
								throw new IllegalArgumentException("Duplicate capability in party role.");
							}
							break;
						}
						case "objective":
						{
							requireElement(child, "objective", List.of("mode", "weight"));
							final ObjectiveMode mode = ObjectiveMode.valueOf(child.getAttribute("mode"));
							if (objectives.put(mode, strictInt(child.getAttribute("weight"), -1000, 1000)) != null)
							{
								throw new IllegalArgumentException("Duplicate objective in party role.");
							}
							break;
						}
						default:
							throw new IllegalArgumentException("Unknown party role catalog element.");
					}
				}
				if (roles.put(roleKey, new RoleDefinition(roleKey, capabilities, objectives, support)) != null)
				{
					throw new IllegalArgumentException("Duplicate party role key.");
				}
			}
			final String hash = HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
			return new PhantomPartyRoleCatalog(roles, hash);
		}
		catch (RuntimeException e)
		{
			throw e;
		}
		catch (Exception e)
		{
			throw new IllegalArgumentException("Could not load strict party role catalog.", e);
		}
	}

	private static List<Element> children(Element parent, String name)
	{
		final List<Element> result = new ArrayList<>();
		for (Element child : childElements(parent))
		{
			if (!name.equals(child.getTagName()))
			{
				throw new IllegalArgumentException("Unknown party role catalog element.");
			}
			result.add(child);
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
				throw new IllegalArgumentException("Unexpected text in party role catalog.");
			}
		}
		return result;
	}

	private static void requireElement(Element element, String name, List<String> attributes)
	{
		if (!name.equals(element.getTagName()) || (element.getAttributes().getLength() != attributes.size()))
		{
			throw new IllegalArgumentException("Invalid party role catalog element.");
		}
		for (String attribute : attributes)
		{
			if (!element.hasAttribute(attribute) || element.getAttribute(attribute).isBlank())
			{
				throw new IllegalArgumentException("Missing party role catalog attribute.");
			}
		}
	}

	private static boolean strictBoolean(String value)
	{
		if ("true".equals(value))
		{
			return true;
		}
		if ("false".equals(value))
		{
			return false;
		}
		throw new IllegalArgumentException("Invalid party role boolean.");
	}

	private static int strictInt(String value, int minimum, int maximum)
	{
		if (!value.matches("-?[0-9]+"))
		{
			throw new IllegalArgumentException("Invalid party role integer.");
		}
		final int result = Integer.parseInt(value);
		if ((result < minimum) || (result > maximum))
		{
			throw new IllegalArgumentException("Party role integer is outside bounds.");
		}
		return result;
	}
}
