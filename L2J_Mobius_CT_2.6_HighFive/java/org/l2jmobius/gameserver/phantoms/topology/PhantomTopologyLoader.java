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
package org.l2jmobius.gameserver.phantoms.topology;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Strict deterministic reader for data/phantoms/topology only.
 */
public final class PhantomTopologyLoader
{
	private static final long MAXIMUM_FILE_BYTES = 4L * 1024 * 1024;

	private final Path _directory;
	private final PhantomTopologyValidationBackend _backend;
	private final PhantomTopologyPolicy _policy;

	public PhantomTopologyLoader(Path directory, PhantomTopologyValidationBackend backend, PhantomTopologyPolicy policy)
	{
		_directory = directory.toAbsolutePath().normalize();
		_backend = java.util.Objects.requireNonNull(backend, "backend");
		_policy = java.util.Objects.requireNonNull(policy, "policy");
	}

	public PhantomTopologySnapshot load(long generation)
	{
		final List<Path> files;
		try (Stream<Path> stream = Files.list(_directory))
		{
			files = stream.filter(path -> Files.isRegularFile(path) && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".xml")).sorted().toList();
		}
		catch (Exception exception)
		{
			throw failure("io", "Unable to list topology data directory.", exception);
		}
		if (files.isEmpty() || (files.size() > _policy.maximumFiles()))
		{
			throw failure("count", "Topology XML file count must be between 1 and policy maximum.");
		}
		final ArrayList<PhantomTopologyNode> nodes = new ArrayList<>();
		final ArrayList<PhantomTopologyAnchor> anchors = new ArrayList<>();
		final ArrayList<PhantomTopologyEdge> edges = new ArrayList<>();
		String datasetId = null;
		int datasetVersion = -1;
		for (Path file : files)
		{
			try
			{
				if (Files.size(file) > MAXIMUM_FILE_BYTES)
				{
					throw failure("count", "Topology XML file exceeds the fixed byte bound.");
				}
				final Document document = parse(file);
				final Element root = document.getDocumentElement();
				requireElement(root, "topology");
				requireAttributes(root, Set.of("schemaVersion", "datasetId", "datasetVersion"));
				final int schemaVersion = requiredInt(root, "schemaVersion");
				if (schemaVersion != 1)
				{
					throw failure("schema", "Unsupported topology schemaVersion.");
				}
				final String currentDatasetId = required(root, "datasetId", 96);
				PhantomTopologyPolicy.requireId(currentDatasetId, "dataset id");
				final int currentDatasetVersion = requiredInt(root, "datasetVersion");
				if (currentDatasetVersion < 1)
				{
					throw failure("schema", "Topology datasetVersion must be positive.");
				}
				if (datasetId == null)
				{
					datasetId = currentDatasetId;
					datasetVersion = currentDatasetVersion;
				}
				else if (!datasetId.equals(currentDatasetId) || (datasetVersion != currentDatasetVersion))
				{
					throw failure("schema", "Topology files declare different dataset metadata.");
				}
				final NodeList children = root.getChildNodes();
				for (int index = 0; index < children.getLength(); index++)
				{
					final Node child = children.item(index);
					if (child.getNodeType() != Node.ELEMENT_NODE)
					{
						if ((child.getNodeType() == Node.TEXT_NODE) && !child.getTextContent().isBlank())
						{
							throw failure("schema", "Unexpected topology root text.");
						}
						continue;
					}
					final Element element = (Element) child;
					switch (element.getTagName())
					{
						case "node" ->
						{
							if (nodes.size() >= _policy.maximumNodes())
							{
								throw failure("count", "Topology node count exceeds policy.");
							}
							nodes.add(parseNode(element));
						}
						case "anchor" ->
						{
							if (anchors.size() >= _policy.maximumAnchors())
							{
								throw failure("count", "Topology anchor count exceeds policy.");
							}
							anchors.add(parseAnchor(element));
						}
						case "edge" ->
						{
							if (edges.size() >= _policy.maximumEdges())
							{
								throw failure("count", "Topology edge count exceeds policy.");
							}
							edges.add(parseEdge(element));
						}
						default -> throw failure("schema", "Unknown topology element.");
					}
				}
			}
			catch (PhantomTopologyValidationException exception)
			{
				throw exception;
			}
			catch (Exception exception)
			{
				throw failure("parse", "Unable to parse topology XML.", exception);
			}
		}
		return PhantomTopologySnapshot.create(1, datasetId, datasetVersion, generation, nodes, anchors, edges, _backend, _policy);
	}

	private Document parse(Path path) throws Exception
	{
		final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
		factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
		factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
		factory.setXIncludeAware(false);
		factory.setExpandEntityReferences(false);
		factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
		factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
		final DocumentBuilder builder = factory.newDocumentBuilder();
		try (InputStream stream = Files.newInputStream(path))
		{
			return builder.parse(stream);
		}
	}

	private PhantomTopologyNode parseNode(Element element)
	{
		final PhantomTopologyArea.Form form = enumValue(PhantomTopologyArea.Form.class, required(element, "form", 32));
		final Set<String> attributes = new HashSet<>(Set.of("id", "kind", "instanceId", "form"));
		if (element.hasAttribute("parentId"))
		{
			attributes.add("parentId");
		}
		if (element.hasAttribute("tags"))
		{
			attributes.add("tags");
		}
		switch (form)
		{
			case POINT_RADIUS -> attributes.addAll(Set.of("x", "y", "z", "radius"));
			case CUBOID -> attributes.addAll(Set.of("minX", "maxX", "minY", "maxY", "minZ", "maxZ"));
			case POLYGON -> attributes.addAll(Set.of("minZ", "maxZ"));
		}
		requireAttributes(element, attributes);
		final int instanceId = requiredInt(element, "instanceId");
		final PhantomTopologyArea area = switch (form)
		{
			case POINT_RADIUS -> PhantomTopologyArea.pointRadius(new PhantomTopologyPoint(requiredInt(element, "x"), requiredInt(element, "y"), requiredInt(element, "z"), instanceId), requiredInt(element, "radius"));
			case CUBOID -> PhantomTopologyArea.cuboid(instanceId, requiredInt(element, "minX"), requiredInt(element, "maxX"), requiredInt(element, "minY"), requiredInt(element, "maxY"), requiredInt(element, "minZ"), requiredInt(element, "maxZ"));
			case POLYGON -> PhantomTopologyArea.polygon(instanceId, requiredInt(element, "minZ"), requiredInt(element, "maxZ"), vertices(element));
		};
		return new PhantomTopologyNode(required(element, "id", 96), enumValue(PhantomTopologyNodeKind.class, required(element, "kind", 32)), instanceId, area, optional(element, "parentId", 96), values(element, "tags"), sources(element, form == PhantomTopologyArea.Form.POLYGON));
	}

	private PhantomTopologyAnchor parseAnchor(Element element)
	{
		final Set<String> attributes = new HashSet<>(Set.of("id", "role", "nodeId", "x", "y", "z", "instanceId", "tolerance"));
		for (String optional : List.of("npcId", "mapRegionLocId", "tags"))
		{
			if (element.hasAttribute(optional))
			{
				attributes.add(optional);
			}
		}
		requireAttributes(element, attributes);
		return new PhantomTopologyAnchor(required(element, "id", 96), enumValue(PhantomTopologyAnchorRole.class, required(element, "role", 32)), required(element, "nodeId", 96), new PhantomTopologyPoint(requiredInt(element, "x"), requiredInt(element, "y"), requiredInt(element, "z"), requiredInt(element, "instanceId")), optionalInt(element, "npcId"), optionalInt(element, "mapRegionLocId"), requiredInt(element, "tolerance"), values(element, "tags"), sources(element, false));
	}

	private PhantomTopologyEdge parseEdge(Element element)
	{
		final Set<String> attributes = new HashSet<>(Set.of("id", "fromNodeId", "toNodeId", "mode", "bidirectional", "baseCost", "baseTravelMillis", "backgroundEligible", "channels"));
		for (String optional : List.of("doorId", "fromAnchorId", "toAnchorId"))
		{
			if (element.hasAttribute(optional))
			{
				attributes.add(optional);
			}
		}
		requireAttributes(element, attributes);
		final Set<PhantomPerceptionChannel> channels = new HashSet<>();
		for (String value : values(element, "channels"))
		{
			if (!channels.add(enumValue(PhantomPerceptionChannel.class, value)))
			{
				throw failure("schema", "Duplicate topology perception channel.");
			}
		}
		return new PhantomTopologyEdge(required(element, "id", 96), required(element, "fromNodeId", 96), required(element, "toNodeId", 96), enumValue(PhantomTopologyEdgeMode.class, required(element, "mode", 32)), requiredBoolean(element, "bidirectional"), requiredInt(element, "baseCost"), requiredLong(element, "baseTravelMillis"), requiredBoolean(element, "backgroundEligible"), channels, optionalInt(element, "doorId"), optional(element, "fromAnchorId", 96), optional(element, "toAnchorId", 96), sources(element, false));
	}

	private List<PhantomTopologyArea.Vertex> vertices(Element element)
	{
		final ArrayList<PhantomTopologyArea.Vertex> result = new ArrayList<>();
		final NodeList children = element.getChildNodes();
		for (int index = 0; index < children.getLength(); index++)
		{
			final Node child = children.item(index);
			if ((child.getNodeType() == Node.ELEMENT_NODE) && ((Element) child).getTagName().equals("vertex"))
			{
				final Element vertex = (Element) child;
				requireAttributes(vertex, Set.of("x", "y"));
				requireNoElementChildren(vertex);
				if (result.size() >= _policy.maximumVertices())
				{
					throw failure("count", "Topology polygon vertex count exceeds policy.");
				}
				result.add(new PhantomTopologyArea.Vertex(requiredInt(vertex, "x"), requiredInt(vertex, "y")));
			}
		}
		return List.copyOf(result);
	}

	private List<String> sources(Element element, boolean verticesAllowed)
	{
		final ArrayList<String> result = new ArrayList<>();
		final NodeList children = element.getChildNodes();
		for (int index = 0; index < children.getLength(); index++)
		{
			final Node child = children.item(index);
			if (child.getNodeType() != Node.ELEMENT_NODE)
			{
				if ((child.getNodeType() == Node.TEXT_NODE) && !child.getTextContent().isBlank())
				{
					throw failure("schema", "Unexpected topology entity text.");
				}
				continue;
			}
			final Element source = (Element) child;
			if (source.getTagName().equals("source"))
			{
				requireAttributes(source, Set.of("path"));
				requireNoElementChildren(source);
				if (result.size() >= _policy.maximumSourceReferences())
				{
					throw failure("count", "Topology source reference count exceeds policy.");
				}
				result.add(required(source, "path", 512));
			}
			else if (!(verticesAllowed && source.getTagName().equals("vertex")))
			{
				throw failure("schema", "Unknown topology entity child element.");
			}
		}
		return List.copyOf(result);
	}

	private static List<String> values(Element element, String attribute)
	{
		final String raw = optional(element, attribute, 1024);
		if ((raw == null) || raw.isBlank())
		{
			return List.of();
		}
		return Arrays.stream(raw.split(",", -1)).map(String::trim).peek(value ->
		{
			if (value.isEmpty())
			{
				throw failure("schema", "Empty topology list value.");
			}
		}).toList();
	}

	private static void requireElement(Element element, String name)
	{
		if ((element == null) || !element.getTagName().equals(name))
		{
			throw failure("schema", "Unexpected topology root element.");
		}
	}

	private static void requireAttributes(Element element, Set<String> allowed)
	{
		final NamedNodeMap attributes = element.getAttributes();
		for (int index = 0; index < attributes.getLength(); index++)
		{
			if (!allowed.contains(attributes.item(index).getNodeName()))
			{
				throw failure("schema", "Unknown topology attribute.");
			}
		}
		for (String required : allowed)
		{
			if (!element.hasAttribute(required) && !Set.of("parentId", "tags", "npcId", "mapRegionLocId", "doorId", "fromAnchorId", "toAnchorId").contains(required))
			{
				throw failure("schema", "Missing topology attribute.");
			}
		}
	}

	private static void requireNoElementChildren(Element element)
	{
		final NodeList children = element.getChildNodes();
		for (int index = 0; index < children.getLength(); index++)
		{
			final Node child = children.item(index);
			if ((child.getNodeType() == Node.ELEMENT_NODE) || ((child.getNodeType() == Node.TEXT_NODE) && !child.getTextContent().isBlank()))
			{
				throw failure("schema", "Topology leaf element has child content.");
			}
		}
	}

	private static String required(Element element, String attribute, int maximumLength)
	{
		final String value = optional(element, attribute, maximumLength);
		if ((value == null) || value.isBlank())
		{
			throw failure("schema", "Missing or empty topology attribute.");
		}
		return value;
	}

	private static String optional(Element element, String attribute, int maximumLength)
	{
		if (!element.hasAttribute(attribute))
		{
			return null;
		}
		final String value = element.getAttribute(attribute).trim();
		if (value.length() > maximumLength)
		{
			throw failure("count", "Topology string exceeds its bound.");
		}
		return value;
	}

	private static int requiredInt(Element element, String attribute)
	{
		try
		{
			return Integer.parseInt(required(element, attribute, 16));
		}
		catch (NumberFormatException exception)
		{
			throw failure("schema", "Invalid topology integer.", exception);
		}
	}

	private static Integer optionalInt(Element element, String attribute)
	{
		if (!element.hasAttribute(attribute))
		{
			return null;
		}
		return requiredInt(element, attribute);
	}

	private static long requiredLong(Element element, String attribute)
	{
		try
		{
			return Long.parseLong(required(element, attribute, 24));
		}
		catch (NumberFormatException exception)
		{
			throw failure("schema", "Invalid topology long.", exception);
		}
	}

	private static boolean requiredBoolean(Element element, String attribute)
	{
		final String value = required(element, attribute, 5);
		if (!value.equals("true") && !value.equals("false"))
		{
			throw failure("schema", "Invalid topology boolean.");
		}
		return Boolean.parseBoolean(value);
	}

	private static <E extends Enum<E>> E enumValue(Class<E> type, String value)
	{
		try
		{
			return Enum.valueOf(type, value);
		}
		catch (IllegalArgumentException exception)
		{
			throw failure("schema", "Invalid topology enum value.", exception);
		}
	}

	private static PhantomTopologyValidationException failure(String category, String message)
	{
		return new PhantomTopologyValidationException(category, message);
	}

	private static PhantomTopologyValidationException failure(String category, String message, Throwable cause)
	{
		return new PhantomTopologyValidationException(category, message, cause);
	}
}
