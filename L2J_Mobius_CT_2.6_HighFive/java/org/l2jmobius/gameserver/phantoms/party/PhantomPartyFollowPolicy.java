/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.party;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Element;

/** Immutable bounds for native companion paths; ordinary group travel keeps its own policy. */
public record PhantomPartyFollowPolicy(boolean enabled, int startDistance, int stopDistance, int replanDistance, int waypointRadius, int maximumDistance, int routeTimeoutMillis)
{
	public PhantomPartyFollowPolicy
	{
		if ((stopDistance < 128) || (stopDistance > 1000) || (startDistance < stopDistance + 32) || (startDistance > 2000) || (replanDistance < 32) || (replanDistance > 1000) || (waypointRadius < 64) || (waypointRadius > 128) || (maximumDistance < startDistance) || (maximumDistance > 12000) || (routeTimeoutMillis < 5000) || (routeTimeoutMillis > 120000))
		{
			throw new IllegalArgumentException("Companion follow policy is outside hard bounds.");
		}
	}

	public static PhantomPartyFollowPolicy defaults()
	{
		return new PhantomPartyFollowPolicy(true, 350, 250, 64, 64, 6000, 30000);
	}

	public static PhantomPartyFollowPolicy load(Path path)
	{
		try
		{
			final byte[] bytes = Files.readAllBytes(path);
			if ((bytes.length < 1) || (bytes.length > 4096) || Files.isSymbolicLink(path))
			{
				throw new IllegalArgumentException("Companion follow policy file is invalid.");
			}
			StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes));
			final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
			factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
			factory.setXIncludeAware(false);
			factory.setExpandEntityReferences(false);
			final Element root = factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes)).getDocumentElement();
			final Set<String> attributes = Set.of("id", "version", "enabled", "startDistance", "stopDistance", "replanDistance", "waypointRadius", "maximumDistance", "routeTimeoutMillis");
			if (!root.getTagName().equals("partyFollowPolicy") || !root.getAttribute("id").equals("high-five-party-follow-v1") || !root.getAttribute("version").equals("1") || (root.getAttributes().getLength() != attributes.size()) || java.util.stream.IntStream.range(0, root.getAttributes().getLength()).anyMatch(index -> !attributes.contains(root.getAttributes().item(index).getNodeName())) || java.util.stream.IntStream.range(0, root.getChildNodes().getLength()).anyMatch(index -> root.getChildNodes().item(index) instanceof Element) || !root.getTextContent().isBlank())
			{
				throw new IllegalArgumentException("Companion follow policy identity or shape is invalid.");
			}
			final String enabled = root.getAttribute("enabled");
			if (!enabled.equals("true") && !enabled.equals("false"))
			{
				throw new IllegalArgumentException("Companion follow enabled value is invalid.");
			}
			return new PhantomPartyFollowPolicy(Boolean.parseBoolean(enabled), integer(root, "startDistance"), integer(root, "stopDistance"), integer(root, "replanDistance"), integer(root, "waypointRadius"), integer(root, "maximumDistance"), integer(root, "routeTimeoutMillis"));
		}
		catch (IllegalArgumentException exception)
		{
			throw exception;
		}
		catch (Exception exception)
		{
			throw new IllegalArgumentException("Could not load companion follow policy.", exception);
		}
	}

	private static int integer(Element root, String name)
	{
		final String value = root.getAttribute(name);
		if (!value.matches("[0-9]+"))
		{
			throw new IllegalArgumentException("Companion follow policy integer is invalid: " + name);
		}
		return Integer.parseInt(value);
	}
}
