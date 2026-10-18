/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.commerce;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

/** Stock and cash bounds; native offers, weapon compatibility, fees and weight remain authoritative. */
public record PhantomAutonomousSupplyPolicy(boolean enabled, int lowWaterHits, int targetHits, long minimumAdenaReserve, int retrySeconds, int journeyMinutes, int maximumProfiles)
{
	public PhantomAutonomousSupplyPolicy
	{
		if (lowWaterHits < 1 || lowWaterHits > 10 || targetHits <= lowWaterHits || targetHits > 100 || minimumAdenaReserve < 0 || retrySeconds < 10 || retrySeconds > 300 || journeyMinutes < 2 || journeyMinutes > 20 || maximumProfiles < 1 || maximumProfiles > 128) { throw new IllegalArgumentException("Autonomous supply policy is outside hard bounds."); }
	}

	public static PhantomAutonomousSupplyPolicy load(Path path)
	{
		try
		{
			if (Files.isSymbolicLink(path) || Files.size(path) > 4096) { throw new IllegalArgumentException("Invalid autonomous supply policy file."); }
			final var factory = DocumentBuilderFactory.newInstance();
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
			factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, ""); factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
			factory.setXIncludeAware(false); factory.setExpandEntityReferences(false);
			final var root = factory.newDocumentBuilder().parse(new ByteArrayInputStream(Files.readAllBytes(path))).getDocumentElement();
			final var attributes = Set.of("id", "version", "enabled", "lowWaterHits", "targetHits", "minimumAdenaReserve", "retrySeconds", "journeyMinutes", "maximumProfiles");
			if (!root.getTagName().equals("autonomousSupplyPolicy") || !root.getAttribute("id").equals("high-five-autonomous-supply-v1") || !root.getAttribute("version").equals("1") || root.getAttributes().getLength() != attributes.size() || java.util.stream.IntStream.range(0, root.getAttributes().getLength()).anyMatch(index -> !attributes.contains(root.getAttributes().item(index).getNodeName())) || !root.getTextContent().isBlank() || java.util.stream.IntStream.range(0, root.getChildNodes().getLength()).anyMatch(index -> root.getChildNodes().item(index).getNodeType() == org.w3c.dom.Node.ELEMENT_NODE)) { throw new IllegalArgumentException("Unknown autonomous supply policy shape/version."); }
			if (!Set.of("true", "false").contains(root.getAttribute("enabled"))) { throw new IllegalArgumentException("Invalid autonomous supply enabled flag."); }
			return new PhantomAutonomousSupplyPolicy(Boolean.parseBoolean(root.getAttribute("enabled")), Integer.parseInt(root.getAttribute("lowWaterHits")), Integer.parseInt(root.getAttribute("targetHits")), Long.parseLong(root.getAttribute("minimumAdenaReserve")), Integer.parseInt(root.getAttribute("retrySeconds")), Integer.parseInt(root.getAttribute("journeyMinutes")), Integer.parseInt(root.getAttribute("maximumProfiles")));
		}
		catch (IllegalArgumentException exception) { throw exception; }
		catch (Exception exception) { throw new IllegalArgumentException("Could not load autonomous supply policy.", exception); }
	}
}
