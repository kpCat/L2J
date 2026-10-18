/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.progression;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

/** Bounded ordinary-life policy; native skill costs and eligibility remain authoritative. */
public record PhantomAutonomousLearningPolicy(boolean enabled, int retrySeconds, int journeyMinutes, int minimumSpReserve, int maximumProfiles)
{
	public PhantomAutonomousLearningPolicy
	{
		if (retrySeconds < 10 || retrySeconds > 300 || journeyMinutes < 2 || journeyMinutes > 20 || minimumSpReserve < 0 || maximumProfiles < 1 || maximumProfiles > 128)
		{
			throw new IllegalArgumentException("Autonomous learning policy is outside hard bounds.");
		}
	}

	public static PhantomAutonomousLearningPolicy defaults() { return new PhantomAutonomousLearningPolicy(true, 30, 8, 0, 128); }

	public static PhantomAutonomousLearningPolicy load(Path path)
	{
		try
		{
			if (Files.isSymbolicLink(path) || Files.size(path) > 4096) { throw new IllegalArgumentException("Invalid autonomous learning policy file."); }
			final byte[] bytes = Files.readAllBytes(path);
			final var factory = DocumentBuilderFactory.newInstance();
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
			factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
			factory.setXIncludeAware(false);
			factory.setExpandEntityReferences(false);
			final var root = factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes)).getDocumentElement();
			final var attributes = Set.of("id", "version", "enabled", "retrySeconds", "journeyMinutes", "minimumSpReserve", "maximumProfiles");
			if (!root.getTagName().equals("autonomousLearningPolicy") || !root.getAttribute("id").equals("high-five-autonomous-learning-v1") || !root.getAttribute("version").equals("1") || root.getAttributes().getLength() != attributes.size() || java.util.stream.IntStream.range(0, root.getAttributes().getLength()).anyMatch(index -> !attributes.contains(root.getAttributes().item(index).getNodeName())) || !root.getTextContent().isBlank() || java.util.stream.IntStream.range(0, root.getChildNodes().getLength()).anyMatch(index -> root.getChildNodes().item(index).getNodeType() == org.w3c.dom.Node.ELEMENT_NODE)) { throw new IllegalArgumentException("Unknown autonomous learning policy shape or version."); }
			final String enabled = root.getAttribute("enabled");
			if (!Set.of("true", "false").contains(enabled)) { throw new IllegalArgumentException("Invalid autonomous learning enabled flag."); }
			return new PhantomAutonomousLearningPolicy(Boolean.parseBoolean(enabled), Integer.parseInt(root.getAttribute("retrySeconds")), Integer.parseInt(root.getAttribute("journeyMinutes")), Integer.parseInt(root.getAttribute("minimumSpReserve")), Integer.parseInt(root.getAttribute("maximumProfiles")));
		}
		catch (IllegalArgumentException exception) { throw exception; }
		catch (Exception exception) { throw new IllegalArgumentException("Could not load autonomous learning policy.", exception); }
	}
}
