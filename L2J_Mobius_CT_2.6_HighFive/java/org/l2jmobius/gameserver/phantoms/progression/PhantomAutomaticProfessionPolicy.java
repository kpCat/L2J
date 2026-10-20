/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.progression;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

/** Phantom-only opt-in; no human ClassMaster or global Noblesse configuration is changed. */
public record PhantomAutomaticProfessionPolicy(boolean enabled, boolean autoNoblesse, int nobleLevel)
{
	public PhantomAutomaticProfessionPolicy
	{
		if (nobleLevel < 76 || nobleLevel > 85) { throw new IllegalArgumentException("Phantom Noblesse threshold must be 76..85."); }
	}
	public static PhantomAutomaticProfessionPolicy defaults() { return new PhantomAutomaticProfessionPolicy(true, true, 80); }
	public static PhantomAutomaticProfessionPolicy load(Path path)
	{
		try
		{
			if (Files.isSymbolicLink(path) || Files.size(path) > 4096) { throw new IllegalArgumentException("Invalid Phantom profession policy file."); }
			final var factory = DocumentBuilderFactory.newInstance();
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
			factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, ""); factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
			factory.setXIncludeAware(false); factory.setExpandEntityReferences(false);
			final var root = factory.newDocumentBuilder().parse(path.toFile()).getDocumentElement();
			final var attributes = Set.of("id", "version", "enabled", "autoNoblesse", "nobleLevel");
			if (!root.getTagName().equals("automaticProfessions") || !root.getAttribute("id").equals("high-five-automatic-professions-v1") || !root.getAttribute("version").equals("1")
				|| root.getAttributes().getLength() != attributes.size() || java.util.stream.IntStream.range(0, root.getAttributes().getLength()).anyMatch(i -> !attributes.contains(root.getAttributes().item(i).getNodeName()))
				|| !root.getTextContent().isBlank() || java.util.stream.IntStream.range(0, root.getChildNodes().getLength()).anyMatch(i -> root.getChildNodes().item(i).getNodeType() == org.w3c.dom.Node.ELEMENT_NODE)
				|| !Set.of("true", "false").contains(root.getAttribute("enabled")) || !Set.of("true", "false").contains(root.getAttribute("autoNoblesse"))) { throw new IllegalArgumentException("Unknown Phantom profession policy shape."); }
			return new PhantomAutomaticProfessionPolicy(Boolean.parseBoolean(root.getAttribute("enabled")), Boolean.parseBoolean(root.getAttribute("autoNoblesse")), Integer.parseInt(root.getAttribute("nobleLevel")));
		}
		catch (IllegalArgumentException failure) { throw failure; }
		catch (Exception failure) { throw new IllegalArgumentException("Could not load Phantom profession policy.", failure); }
	}
}
