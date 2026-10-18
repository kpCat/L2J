/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.questinstance;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationCatalog.CareerArchetype;

/** Explicit supported career choices; native content still owns every prerequisite and reward. */
public record PhantomAutonomousCareerPolicy(boolean enabled, int retrySeconds, int journeyMinutes, int maximumProfiles, List<Route> routes)
{
	public PhantomAutonomousCareerPolicy
	{
		routes = List.copyOf(routes);
		if (retrySeconds < 10 || retrySeconds > 300 || journeyMinutes < 15 || journeyMinutes > 180 || maximumProfiles < 1 || maximumProfiles > 128 || routes.isEmpty() || routes.size() > 16
			|| routes.stream().map(route -> route.fromClass() + ":" + route.archetype()).distinct().count() != routes.size()) { throw new IllegalArgumentException("Invalid bounded autonomous career policy."); }
	}

	public record Route(String contentId, int fromClass, int toClass, CareerArchetype archetype)
	{
		public Route
		{
			if (contentId == null || !contentId.matches("class\\.[a-z0-9.-]{1,56}") || fromClass < 0 || toClass < 1 || fromClass == toClass || archetype == null) { throw new IllegalArgumentException("Invalid explicit career route."); }
		}
	}

	public static PhantomAutonomousCareerPolicy defaults()
	{
		return new PhantomAutonomousCareerPolicy(true, 30, 90, 128, List.of(new Route("class.warrior-q401", 0, 1, CareerArchetype.DAMAGE)));
	}

	public static PhantomAutonomousCareerPolicy load(Path path)
	{
		try
		{
			if (Files.isSymbolicLink(path) || Files.size(path) > 8192) { throw new IllegalArgumentException("Invalid autonomous career policy file."); }
			final var factory = DocumentBuilderFactory.newInstance();
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
			factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, ""); factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
			factory.setXIncludeAware(false); factory.setExpandEntityReferences(false);
			final var root = factory.newDocumentBuilder().parse(new ByteArrayInputStream(Files.readAllBytes(path))).getDocumentElement();
			final var attributes = Set.of("id", "version", "enabled", "retrySeconds", "journeyMinutes", "maximumProfiles");
			if (!root.getTagName().equals("autonomousCareerPolicy") || !root.getAttribute("id").equals("high-five-autonomous-career-v1") || !root.getAttribute("version").equals("1")
				|| root.getAttributes().getLength() != attributes.size() || java.util.stream.IntStream.range(0, root.getAttributes().getLength()).anyMatch(index -> !attributes.contains(root.getAttributes().item(index).getNodeName()))
				|| !Set.of("true", "false").contains(root.getAttribute("enabled")) || !root.getTextContent().isBlank()) { throw new IllegalArgumentException("Unknown autonomous career policy shape/version."); }
			final List<Route> routes = new java.util.ArrayList<>();
			for (var node = root.getFirstChild(); node != null; node = node.getNextSibling())
			{
				if (node.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) { continue; }
				final var element = (org.w3c.dom.Element) node;
				final var routeAttributes = Set.of("content", "fromClass", "toClass", "archetype");
				if (!element.getTagName().equals("route") || element.getAttributes().getLength() != routeAttributes.size()
					|| java.util.stream.IntStream.range(0, element.getAttributes().getLength()).anyMatch(index -> !routeAttributes.contains(element.getAttributes().item(index).getNodeName()))
					|| element.hasChildNodes()) { throw new IllegalArgumentException("Unknown autonomous career route shape."); }
				routes.add(new Route(element.getAttribute("content"), Integer.parseInt(element.getAttribute("fromClass")), Integer.parseInt(element.getAttribute("toClass")), CareerArchetype.valueOf(element.getAttribute("archetype"))));
			}
			return new PhantomAutonomousCareerPolicy(Boolean.parseBoolean(root.getAttribute("enabled")), Integer.parseInt(root.getAttribute("retrySeconds")), Integer.parseInt(root.getAttribute("journeyMinutes")), Integer.parseInt(root.getAttribute("maximumProfiles")), routes);
		}
		catch (IllegalArgumentException exception) { throw exception; }
		catch (Exception exception) { throw new IllegalArgumentException("Could not load autonomous career policy.", exception); }
	}
}
